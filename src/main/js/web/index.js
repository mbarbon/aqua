// @flow
import React from 'react'
import ReactDOM from 'react-dom'
import './index.css'
import App from './App'
import AppPrerender from './AppPrerender'
import registerServiceWorker from './registerServiceWorker'
import '../../../../src/main/resources/public/css/aqua.css'

if (window.location.hostname !== 'pre-render') {
  let rootNode = window.document.getElementById('root')
  if (document.getElementById('root-marker')) {
    ReactDOM.render(<App />, rootNode)
  } else {
    ReactDOM.hydrate(<App />, rootNode)
  }
  registerServiceWorker()
} else {
  // used by pre-render.js, which runs under node
  global.aqua_exports = { App: AppPrerender }
}
