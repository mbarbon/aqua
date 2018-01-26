// @flow
import React, { PureComponent } from 'react'
import './Spinner.css'

export default class Spinner extends PureComponent<{}> {
  render () {
    return <div className='loader'>Loading...</div>
  }
}
