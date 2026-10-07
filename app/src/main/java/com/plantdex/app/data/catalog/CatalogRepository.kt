package com.plantdex.app.data.catalog

import android.content.Context

/** 앱에 내장된 도감 목록 (assets/catalogs.json). 처음 쓸 때 한 번만 읽습니다. */
class CatalogRepository(private val context: Context) {
    val catalog: PlantCatalog by lazy {
        context.assets.open(ASSET).use { PlantCatalog.parse(it) }
    }

    private companion object {
        const val ASSET = "catalogs.json"
    }
}
