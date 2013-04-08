(defproject clj-gcon "0.0.1-SNAPSHOT"
  :description "Genome Connector: Clojure API to access multiple genomic resources."
  :url "https://github.com/chapmanb/clj-gcon"
  :license {:name "MIT" :url "http://www.opensource.org/licenses/mit-license.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [environ "0.4.0"]
                 [me.raynes/fs "1.4.1"]
                 [clj-genomespace "0.1.3"]
                 [clj-blend "0.1.1-SNAPSHOT"]
                 [org.clojure/tools.logging "0.2.6"]
                 [org.jclouds/jclouds-allblobstore "1.5.9"]]
  :plugins [[lein-midje "3.0.1"]
            [lein-environ "0.4.0"]]
  :profiles {:dev {:dependencies [[midje "1.5.1"]]}})
