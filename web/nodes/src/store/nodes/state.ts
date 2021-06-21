import { Node, SnmpInterface } from '@/types'

export interface State {
  nodes: Node[]
  totalCount: number
  node: Node
  snmpInterfaces: SnmpInterface[]
  snmpInterfacesTotalCount: number
}

const state: State = {
  nodes: [],
  node: {} as Node,
  totalCount: 0,
  snmpInterfaces: [],
  snmpInterfacesTotalCount: 0
}

export default () => state
