package com.lubrcapp.lubrcapp.core.repository.usecase

import com.lubrcapp.lubrcapp.core.repository.BusinessBootstrap
import com.lubrcapp.lubrcapp.core.repository.BusinessSetupRepository
import com.lubrcapp.lubrcapp.core.repository.CreateInitialBusinessRequest
import javax.inject.Inject

class CreateInitialBusinessUseCase @Inject constructor(
    private val repository: BusinessSetupRepository,
) {
    suspend operator fun invoke(
        request: CreateInitialBusinessRequest,
    ): BusinessBootstrap = repository.createInitialBusiness(request)
}
