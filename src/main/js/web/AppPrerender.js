// @flow
import React, { PureComponent } from 'react'
import UserMode from './UserMode'

export default class AppPrerender extends PureComponent<{}> {
  render () {
    return (
      <UserMode queuePosition={null} loadingState={UserMode.MODE_PRERENDER} />
    )
  }
}
