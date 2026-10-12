package com.plantdex.app.data.repository

import android.net.Uri
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storageMetadata
import com.plantdex.app.data.model.CaptureLocation
import com.plantdex.app.data.model.CapturedPhoto
import com.plantdex.app.data.model.CollectionEntry
import com.plantdex.app.data.model.PlantCandidate
import com.plantdex.app.data.model.UserProfile
import com.plantdex.app.util.ImageUtils
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 도감 기록 저장소.
 *
 * Firestore `entries` 컬렉션 하나에 모든 사용자의 기록을 저장하고,
 * 사진은 Storage `users/{uid}/entries/{entryId}.jpg`, 썸네일은 `{entryId}_thumb.jpg` 에 저장합니다.
 */
class CollectionRepository(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val functions: FirebaseFunctions,
) {
    private val entries get() = firestore.collection(ENTRIES)

    /**
     * 사진과 썸네일을 올린 뒤 서버 함수(createEntry)로 기록을 만듭니다.
     * 서버가 위치 필수 · 같은 종 10m 규칙을 확인하고, 통과하면 생성된 기록 id 를 돌려줍니다.
     */
    suspend fun addEntry(
        owner: UserProfile,
        photo: CapturedPhoto,
        plant: PlantCandidate,
        memo: String,
        isPublic: Boolean,
    ): String {
        val id = entries.document().id
        val folder = storage.reference.child("users/${owner.uid}/entries")
        val photoRef = folder.child("$id.jpg")
        val thumbRef = folder.child("${id}_thumb.jpg")
        val jpeg = storageMetadata { contentType = "image/jpeg" }
        val thumbFile = ImageUtils.makeThumbnail(photo.file, File(photo.file.parentFile, "${id}_thumb.jpg"))
        try {
            photoRef.putFile(Uri.fromFile(photo.file), jpeg).await()
            thumbRef.putFile(Uri.fromFile(thumbFile), jpeg).await()
            val location = photo.location
            functions.getHttpsCallable("createEntry").call(
                mapOf(
                    "entryId" to id,
                    "scientificName" to plant.scientificName,
                    "commonName" to plant.commonNames.firstOrNull(),
                    "commonNameLanguage" to plant.namesLanguage.takeIf { plant.commonNames.isNotEmpty() },
                    "gbifId" to plant.gbifId,
                    "family" to plant.family,
                    "genus" to plant.genus,
                    "score" to plant.score,
                    "capturedAt" to photo.capturedAt,
                    "location" to location?.let {
                        mapOf(
                            "latitude" to it.latitude,
                            "longitude" to it.longitude,
                            "accuracyMeters" to it.accuracyMeters?.toDouble(),
                            "placeName" to it.placeName,
                        )
                    },
                    "memo" to memo.trim(),
                    "isPublic" to isPublic,
                ),
            ).await()
            return id
        } catch (e: Exception) {
            // 기록을 만들지 못했으면 올려 둔 사진을 지웁니다.
            withContext(NonCancellable) {
                runCatching { photoRef.delete().await() }
                runCatching { thumbRef.delete().await() }
            }
            throw if (e is FirebaseFunctionsException) e.toSaveException() else e
        } finally {
            thumbFile.delete()
        }
    }

    private fun FirebaseFunctionsException.toSaveException(): Exception {
        val details = details as? Map<*, *>
        return when {
            message == "NEARBY_DUPLICATE" -> NearbyDuplicateException(
                entryId = details?.get("entryId") as? String,
                distanceMeters = (details?.get("distance") as? Number)?.toDouble() ?: 0.0,
            )
            message == "LOCATION_REQUIRED" -> EntrySaveException("위치 정보가 있어야 등록할 수 있어요.")
            code == FirebaseFunctionsException.Code.NOT_FOUND ->
                EntrySaveException("서버 기능이 아직 배포되지 않았어요. (firebase deploy --only functions)")
            code == FirebaseFunctionsException.Code.UNAVAILABLE -> EntrySaveException("네트워크 연결을 확인해 주세요.")
            else -> EntrySaveException("저장하지 못했습니다: ${message.orEmpty()}")
        }
    }

    /** 내 도감 (공개/비공개 모두). */
    fun observeMyEntries(uid: String): Flow<List<CollectionEntry>> =
        entries.whereEqualTo("ownerId", uid)
            .orderBy("capturedAt", Query.Direction.DESCENDING)
            .observe()

    /**
     * 내 기록을 서버에서 직접 한 번 읽습니다 (오프라인 캐시 사용 안 함).
     * 등록 규칙처럼 다른 기기에서 저장한 기록까지 확실히 봐야 할 때 씁니다.
     */
    suspend fun fetchMyEntriesFromServer(uid: String): List<CollectionEntry> =
        entries.whereEqualTo("ownerId", uid)
            .get(Source.SERVER)
            .await()
            .documents
            .mapNotNull { it.toEntry() }

    /** 다른 사용자의 공개 도감. */
    fun observePublicEntriesOf(uid: String): Flow<List<CollectionEntry>> =
        entries.whereEqualTo("ownerId", uid)
            .whereEqualTo("isPublic", true)
            .orderBy("capturedAt", Query.Direction.DESCENDING)
            .observe()

    /** 모든 사용자의 최근 공개 기록. */
    fun observeFeed(limit: Long = 100): Flow<List<CollectionEntry>> =
        entries.whereEqualTo("isPublic", true)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .observe()

    fun observeEntry(id: String): Flow<CollectionEntry?> = callbackFlow {
        val registration = entries.document(id).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.toEntry())
        }
        awaitClose { registration.remove() }
    }

    suspend fun setPublic(id: String, isPublic: Boolean) {
        entries.document(id).update("isPublic", isPublic).await()
    }

    suspend fun deleteEntry(entry: CollectionEntry) {
        entries.document(entry.id).delete().await()
        runCatching {
            storage.reference.child("users/${entry.ownerId}/entries/${entry.id}.jpg").delete().await()
        }
    }

    private fun Query.observe(): Flow<List<CollectionEntry>> = callbackFlow {
        val registration = addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.documents?.mapNotNull { it.toEntry() }.orEmpty())
        }
        awaitClose { registration.remove() }
    }

    private fun DocumentSnapshot.toEntry(): CollectionEntry? {
        if (!exists()) return null
        val geoPoint = getGeoPoint("location")
        return CollectionEntry(
            id = id,
            ownerId = getString("ownerId") ?: return null,
            ownerName = getString("ownerName").orEmpty(),
            scientificName = getString("scientificName") ?: return null,
            commonName = getString("commonName"),
            commonNameLanguage = getString("commonNameLanguage"),
            gbifId = getString("gbifId"),
            family = getString("family"),
            genus = getString("genus"),
            score = getDouble("score") ?: 0.0,
            photoUrl = getString("photoUrl").orEmpty(),
            capturedAt = getTimestamp("capturedAt")?.toDate()?.time ?: 0L,
            location = geoPoint?.let {
                CaptureLocation(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    accuracyMeters = getDouble("locationAccuracy")?.toFloat(),
                    placeName = getString("placeName"),
                )
            },
            memo = getString("memo").orEmpty(),
            isPublic = getBoolean("isPublic") ?: false,
            likeCount = getLong("likeCount")?.toInt()?.coerceAtLeast(0) ?: 0,
            thumbUrl = getString("thumbUrl"),
            commentCount = getLong("commentCount")?.toInt()?.coerceAtLeast(0) ?: 0,
            isHidden = getString("moderation") == "hidden",
            geohash = getString("geohash"),
        )
    }

    companion object {
        const val ENTRIES = "entries"
    }
}

/** 같은 종을 이미 [distanceMeters] 거리에 등록해서 서버가 거절함 */
class NearbyDuplicateException(val entryId: String?, val distanceMeters: Double) :
    Exception("같은 식물을 ${distanceMeters.toInt()}m 거리에 이미 등록했어요.")

class EntrySaveException(message: String) : Exception(message)
