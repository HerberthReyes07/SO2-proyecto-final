package com.redxela.api;

import com.redxela.domain.ServiceLevel;

/** Nivel de servicio tal como lo ve la interfaz; el tiempo objetivo se expresa en minutos simulados. */
public record ServiceLevelInfo(String id, int level, String displayName, long targetWaitMinutes) {

    static ServiceLevelInfo from(ServiceLevel serviceLevel) {
        return new ServiceLevelInfo(
                serviceLevel.name(),
                serviceLevel.level(),
                serviceLevel.displayName(),
                serviceLevel.targetWait().toMinutes());
    }
}
