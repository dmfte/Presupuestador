package com.tuapp.fintrack.domain.model

import com.tuapp.fintrack.data.model.PayCycle

data class PayCycleWithEffectiveDate(
    val cycle: PayCycle,
    val rule: PayCycleRule,
    val nextNominalDateMs: Long,
    val nextEffectiveDateMs: Long
)
