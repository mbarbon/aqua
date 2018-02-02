// @flow
import React, { PureComponent } from 'react'
import { BrowserRouter } from 'react-router-dom'
import UserMode from './UserMode'

export default class AppPrerender extends PureComponent<{}> {
  render () {
    return (
      <BrowserRouter>
        <UserMode queuePosition={null} loadingState={UserMode.MODE_PRERENDER} />
      </BrowserRouter>
    )
  }
}
