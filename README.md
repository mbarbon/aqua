## Aqua

A simplistic anime recommendation system: try it at [aqua-recommend.net](https://aqua-recommend.net/)

## How it works

The core of the system is a set of well-known recommendation algorithms, using rating data from
[MyAnimeList](https://myanimelist.net/).

Currently implemented algorithms are:

* co-occurrency collaborative filtering ("other users also liked..." recommendations)<br>
  "Item-based top-N recommendation algorithms" by Mukund Deshpande and George Karypis (2004)
* memory-based colleborative filtering using vector dot product as similarity measure<br>
  [WikiPedia link](https://en.wikipedia.org/wiki/Collaborative_filtering#Memory-based)
* latent factor decomposition (LFD) matrix factorization using alternating least squares<br>
  [blog.insightdatascience.com link](https://blog.insightdatascience.com/explicit-matrix-factorization-als-sgd-and-all-that-jazz-b00e4d9b21ea)
* memory-based collaborative filtering using LFD output vectors instead of the raw rating vectors

In my completely unbiased evaluation, Aqua provides decent recommendations, but keep in mind that this is mostly
a pet project to learn more about fields I was not familiar with before (recommendations, machine learning) and to
check out some technologies (Clojure, React, React Native). In other words: I think the end result is useful, but
creating a finished product was never the goal.

## LICENSE

Copyright 2017 Mattia Barbon

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.

3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
