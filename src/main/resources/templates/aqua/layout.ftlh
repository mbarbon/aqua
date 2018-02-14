<#compress>
<html>

<head>
  <!-- Google Analytics -->
  <script>
    var analyticsDebugIfLocalHost = location.hostname == 'localhost';
    var analyticsLibrary = analyticsDebugIfLocalHost ? 'analytics_debug.js' : 'analytics.js';

    (function (i, s, o, g, r, a, m) {
      i['GoogleAnalyticsObject'] = r; i[r] = i[r] || function () {
        (i[r].q = i[r].q || []).push(arguments)
      }, i[r].l = 1 * new Date(); a = s.createElement(o),
        m = s.getElementsByTagName(o)[0]; a.async = 1; a.src = g; m.parentNode.insertBefore(a, m)
    })(window, document, 'script', 'https://www.google-analytics.com/' + analyticsLibrary, 'ga');

    ga('create', 'UA-103130411-1', 'auto');

    if (analyticsDebugIfLocalHost) {
      ga('set', 'sendHitTask', null);
    }
  </script>
  <!-- End Google Analytics -->
  <meta name=viewport content="width=device-width, initial-scale=1">
  <meta name="description" content="${meta.description ! "Get anime recommendations based on your ratings; you can use your MyAnimelist account as well"}">
  <#if meta.noindex??><meta name="robots" content="noindex, follow"></#if>
  <link rel="shortcut icon" href="/favicon.ico?uniq=2" />
  <link rel="manifest" href="/manifest.json">
  <link rel="stylesheet" href="/css/aqua.css?r=2">
  <title>${meta.title ! "Aqua: anime recommendations"}</title>
</head>

<body>
  <#include aquaBodyTemplate>
</body>

</html>
</#compress>