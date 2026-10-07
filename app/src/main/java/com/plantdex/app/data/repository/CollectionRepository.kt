package com.plantdex.app.data.repository

import android.net.Uri
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storageMetadata
import com.plantdex.app.data.model.CaptureLocation
import com.plantdex.app.data.model.CapturedPhoto
import com.plantdex.app.data.model.CollectionEntry
import com.plantdex.app.data.model.PlantCandidate
import com.plantdex.app.data.model.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * 도감 기록 저장소.
 *
 * Firestore `entries` 컬렉션 하나에 모든 사용자의 기록을 저장하고,
 * 사진은 Storage `users/{uid}/entries/{entryId}.jpg` 에 저장합니다.
 */
class CollectionRepository(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
) {
    private val entries get() = firestore.collection(ENTRIES)

    /** 사진을 업로드하고 도감 기록을 생성합니다. 생성된 기록 id 를 반환합니다. */
    suspend fun addEntry(
        owner: UserProfile,
        photo: CapturedPhoto,
        plant: PlantCandidate,
        memo: String,
        isPublic: Boolean,
    ): String {
        val doc = entries.document()
        val photoRef = storage.reference.child("users/${owner.uid}/entries/${doc.id}.jpg")
        photoRef.putFile(Uri.fromFile(photo.file), storageMetadata { contentType = "image/jpeg" }).await()
        val photoUrl = photoRef.downloadUrl.await().toString()

        val location = photo.location
        doc.set(
            mapOf(
                "ownerId" to owner.uid,
                "ownerName" to owner.displayName,
                "scientificName" to plant.scientificName,
                "commonName" to plant.commonNames.firstOrNull(),
                "commonNameLanguage" to plant.namesLanguage.takeIf { plant.commonNames.isNotEmpty() },
                "gbifId" to plant.gbifId,
                "family" to plant.family,
                "genus" to plant.genus,
                "score" to plant.score,
                "photoUrl" to photoUrl,
                "photoPath" to photoRef.path,
                "capturedAt" to Timestamp(Date(photo.capturedAt)),
                "location" to location?.let { GeoPoint(it.latitude, it.longitude) },
                "locationAccuracy" to location?.accuracyMeters?.toDouble(),
                "placeName" to location?.placeName,
                "memo" to memo.trim(),
                "isPublic" to isPublic,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        return doc.id
    }

    /** 내 도감 (공개/비공개 모두). */
    fun observeMyEntries(uid: String): Flow<List<CollectionEntry>> =
        entries.whereEqualTo("ownerId", uid)
            .orderBy("capturedAt", Query.Direction.DESCENDING)
            .observe()

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
        )
    }

    companion object {
        const val ENTRIES = "entries"
    }
}
