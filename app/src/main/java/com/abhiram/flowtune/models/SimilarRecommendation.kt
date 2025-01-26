package com.abhiram.flowtune.models

import com.abhiram.flowtune.db.entities.LocalItem
import com.zionhuang.innertube.models.YTItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
