import { getJson, sendJson } from '@/services/apiClient'
import type { CreateCustomerRequest, Customer } from '@/types/catalog'

export function fetchCustomers(): Promise<Customer[]> {
  return getJson<Customer[]>('/api/customers')
}

export function createCustomer(request: CreateCustomerRequest): Promise<Customer> {
  return sendJson<Customer>('/api/customers', 'POST', request)
}
