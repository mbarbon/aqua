// @flow
import React, { PureComponent } from 'react'
import { BrowserRouter, Route, Switch } from 'react-router-dom'
import Main from './Main'

class App extends PureComponent<{}> {
  render () {
    return (
      <BrowserRouter>
        <Switch>
          <Route exact path='/' component={Main} />
        </Switch>
      </BrowserRouter>
    )
  }
}

export default App
