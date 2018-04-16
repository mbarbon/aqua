// @flow

class DelayedAction {
  timerId: ?number
  callback: ?() => void

  setAction (delayMs: number, callback: () => void) {
    this.cancel()
    this.callback = callback
    this.timerId = setTimeout(this._onTimer.bind(this), delayMs)
  }

  cancel () {
    if (this.timerId) {
      clearTimeout(this.timerId)
    }
    this.callback = null
  }

  _onTimer () {
    this.timerId = null
    if (this.callback) {
      this.callback()
    }
  }
}

export default DelayedAction
