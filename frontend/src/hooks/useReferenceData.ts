import { fetchCatalog } from '@/services/catalogApi'
import { fetchCustomers } from '@/services/customerApi'
import { useRemoteData } from '@/hooks/useRemoteData'

/** Catálogo (productos, tipos de mercancía, niveles de servicio): no cambia mientras corre la aplicación. */
export function useCatalog() {
  return useRemoteData(fetchCatalog)
}

/** Lista de clientes; `reload` la refresca tras crear una cuenta. */
export function useCustomers() {
  return useRemoteData(fetchCustomers)
}
