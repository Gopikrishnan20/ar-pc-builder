package com.arpcbuilder.domain.repository

import com.arpcbuilder.domain.model.ComponentType
import com.arpcbuilder.domain.model.PCComponent
import kotlinx.coroutines.flow.Flow

/** Source of the parts catalogue. Swap the implementation for a network or Room source later. */
interface ComponentRepository {
    fun observeCatalog(): Flow<List<PCComponent>>
    fun observeByType(type: ComponentType): Flow<List<PCComponent>>
}
