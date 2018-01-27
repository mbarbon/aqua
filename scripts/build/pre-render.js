const React = require('react')
const ReactDOMServer = require('react-dom/server')
const jsdom = require('jsdom')
const fs = require('fs')
const path = require('path')
const glob = require('glob')

var paths = glob.sync('build/public/static/js/main.*.js')

const file = 'build/public/index.html'
const rawHtml = fs.readFileSync(file, {
  encoding: 'utf-8'
})
const dom = new jsdom.JSDOM(rawHtml, {
  url: 'http://pre-render'
})

global.window = dom.window
global.document = window.document
global.navigator = window.navigator

global.window.localStorage = {
  getItem: function () {
    return null
  }
}

require(path.join('../..', paths[0]))
const { App } = global.aqua_exports

try {
  const html = ReactDOMServer.renderToString(React.createElement(App, {}))
  const div = dom.window.document.getElementById('root')

  div.innerHTML = html

  fs.writeFileSync(file, dom.window.document.documentElement.outerHTML)
} catch (e) {
  console.error('Error rendering file: ' + file)
  throw e
}
