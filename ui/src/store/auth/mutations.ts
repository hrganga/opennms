import { WhoAmIResponse } from '@/types'
import { State } from './state'

const SAVE_WHO_AM_I_TO_STATE = (state: State, whoAmi: WhoAmIResponse) => {
  state.whoAmi = whoAmi
}

export default {
  SAVE_WHO_AM_I_TO_STATE
}
