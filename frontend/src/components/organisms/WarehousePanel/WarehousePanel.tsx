import { useMemo, useState } from 'react'
import { AisleGrid } from '@/components/molecules/AisleGrid'
import { LotTable } from '@/components/molecules/LotTable'
import { StatCard } from '@/components/molecules/StatCard'
import { StrategySelector } from '@/components/molecules/StrategySelector'
import { TypeLegend } from '@/components/molecules/TypeLegend'
import type { PlacementStrategyId, StrategyInfo } from '@/types/catalog'
import type { WarehouseState } from '@/types/simulation'
import { buildAisles, typeColor } from '@/utils/warehouseMap'
import { occupancyPercent, wastePercent } from '@/utils/warehouseStats'
import styles from './WarehousePanel.module.css'

export interface WarehousePanelProps {
  warehouse: WarehouseState
  strategies: readonly StrategyInfo[]
  /** Diccionario id de producto → nombre mostrado. */
  productNames: Record<string, string>
  /** Diccionario id de producto → id de su tipo de mercancía. */
  productTypeIds: Record<string, string>
  /** Diccionario id de tipo de mercancía → nombre mostrado. */
  merchandiseTypeNames: Record<string, string>
  onStrategyChange: (strategy: PlacementStrategyId) => void
  strategyError?: string | null
}

/**
 * Almacén: mapa de ubicaciones por pasillo, lotes y métricas de ocupación y fragmentación. Apuntar un
 * lote en la tabla resalta en el mapa las ubicaciones en que está repartido.
 */
export function WarehousePanel({
  warehouse,
  strategies,
  productNames,
  productTypeIds,
  merchandiseTypeNames,
  onStrategyChange,
  strategyError,
}: WarehousePanelProps) {
  const [highlightedLotId, setHighlightedLotId] = useState<string | null>(null)
  const { layout } = warehouse

  const aisles = useMemo(
    () => buildAisles(warehouse, { productNames, productTypeIds }, highlightedLotId),
    [warehouse, productNames, productTypeIds, highlightedLotId],
  )
  const legendItems = Object.entries(merchandiseTypeNames).map(([id, label]) => ({ id, label, color: typeColor(id) }))
  const scatteredLots = warehouse.lots.filter((lot) => lot.runs > 1).length

  return (
    <section aria-labelledby="warehouse-title">
      <h2 id="warehouse-title" className={styles.title}>
        Almacén
      </h2>

      <div className={styles.cards}>
        <StatCard
          label="Ubicaciones ocupadas"
          value={`${warehouse.usedLocations} / ${warehouse.totalLocations}`}
          hint={`${occupancyPercent(warehouse)}% del almacén`}
        />
        <StatCard
          label="Espacio perdido"
          value={`${wastePercent(warehouse)}%`}
          hint="fragmentación interna de las ubicaciones ocupadas"
        />
        <StatCard label="Lotes" value={String(warehouse.lots.length)} hint={`${scatteredLots} dispersos`} />
        <StatCard
          label="Unidades rechazadas"
          value={String(warehouse.rejectedUnits)}
          hint="por no haber ubicaciones libres"
        />
      </div>

      <StrategySelector
        strategies={strategies}
        value={warehouse.strategy}
        onChange={onStrategyChange}
        error={strategyError}
      />

      <h3 className={styles.subtitle}>Mapa de ubicaciones</h3>
      <p className={styles.note}>
        Cada pasillo se ve de frente: una columna por posición y una fila por nivel (el más alto arriba). Una
        ubicación se llena de abajo hacia arriba según el volumen que ocupa; las vacías tienen borde punteado.
      </p>
      <TypeLegend items={legendItems} />
      <div className={styles.aisles}>
        {aisles.map((cells, aisle) => (
          <AisleGrid
            key={aisle}
            title={`Pasillo ${aisle + 1}`}
            positions={layout.positions}
            levels={layout.levels}
            cells={cells}
          />
        ))}
      </div>

      <h3 className={styles.subtitle}>Lotes</h3>
      <LotTable
        lots={warehouse.lots}
        productNames={productNames}
        highlightedLotId={highlightedLotId}
        onHighlight={setHighlightedLotId}
      />
    </section>
  )
}
