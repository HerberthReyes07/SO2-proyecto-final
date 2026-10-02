package com.redxela.api;

import com.redxela.domain.MerchandiseType;
import com.redxela.domain.ResourceType;
import java.util.List;

/** Tipo de mercancía tal como lo ve la interfaz. */
public record MerchandiseTypeInfo(String id, String displayName, String description, List<ResourceType> recipe) {

    static MerchandiseTypeInfo from(MerchandiseType type) {
        return new MerchandiseTypeInfo(type.name(), type.displayName(), type.description(), type.recipe());
    }
}
