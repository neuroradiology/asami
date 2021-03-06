(defproject org.clojars.quoll/asami "0.2.17"
  :description "An in-memory implementation of a graph store, implementing the naga-store protocol"
  :url "http://github.com/threatgrid/asami"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/clojurescript "1.10.439"]
                 [prismatic/schema "1.1.7"] 
                 [org.clojure/core.cache "0.7.1"]
                 [org.clojars.quoll/naga-store "0.2.1"]]
  :plugins [[lein-cljsbuild "1.1.7"]]
  :cljsbuild {
    :builds {
      :dev
      {:source-paths ["src"]
       :compiler {
         :output-to "out/asami/core.js"
         :optimizations :simple
         :pretty-print true}}
      :test
      {:source-paths ["src" "test"]
       :compiler {
         :output-to "out/asami/test_memory.js"
         :optimizations :simple
         :pretty-print true}}
      }
    :test-commands {
      "unit" ["node" "out/asami/test_memory.js"]}
    })
