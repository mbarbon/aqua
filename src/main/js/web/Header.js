// @flow
import React, { PureComponent } from 'react'
import { aquaAutocomplete, localState } from '../shared/state/Globals'

export default class Header extends PureComponent<{
  userMode: string,
  malUsername: ?string,
  queuePosition: ?number,
  canRefresh: boolean
}> {
  changeUser (e: SyntheticEvent<>) {
    e.preventDefault()
    localState.resetUserMode()
  }

  refreshMalList () {
    let malUsername = this.props.malUsername
    if (malUsername === null || malUsername === undefined) {
      throw new Error('Invalid calls to refreshMailLis')
    }
    localState.loadMalRecommendations(malUsername)
  }

  refreshLocalRecommendations () {
    localState.loadLocalRecommendations()
    aquaAutocomplete.setTerm('')
  }

  render () {
    let isMal = !!this.props.malUsername

    return (
      <div className='main-page-head aqua-body' id='main-page-head'>
        <div className='main-page-head-actions'>
          {this.props.userMode === 'local' && (
            <div className='aqua-button inline-aqua-button'>
              <a href='#' onClick={this.changeUser.bind(this)}>
                Use a MAL account
              </a>
              <br />
            </div>
          )}
          {isMal && <b>{this.props.malUsername}</b>}
          {this.props.queuePosition &&
            `(position in queue ${this.props.queuePosition}`}
          {this.props.canRefresh &&
            !this.props.queuePosition && (
              <div className='aqua-button inline-aqua-button small-aqua-button'>
                <a
                  href='#'
                  onClick={
                    isMal
                      ? this.refreshMalList.bind(this)
                      : this.refreshLocalRecommendations.bind(this)
                  }
                >
                  Refresh
                </a>
              </div>
            )}{' '}
          {this.props.userMode === 'mal' && (
            <div className='aqua-button inline-aqua-button small-aqua-button'>
              <a href='change-user' onClick={this.changeUser.bind(this)}>
                Change user
              </a>
            </div>
          )}
        </div>
        <div className='main-page-logo'>
          <img src='aqua-thumbsup.jpg' />
          <div className='start-page-head-text'>
            <div className='first-head'>Aqua</div>
            <div className='second-head'>anime recommendations</div>
          </div>
        </div>
      </div>
    )
  }
}
