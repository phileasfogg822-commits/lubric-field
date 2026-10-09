package com.lubrcapp.lubrcapp.core.repository.usecase

import com.lubrcapp.lubrcapp.core.repository.BusinessBootstrap
import com.lubrcapp.lubrcapp.core.repository.BusinessSetupRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveBusinessBootstrapUseCase @Inject constructor(
    private val repository: BusinessSetupRepository,
) {
    operator fun invoke(): Flow<BusinessBootstrap?> = repository.observeBootstrap()
}
