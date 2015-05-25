(defproject ipfix-reader "0.1.0"
  :description "Basic parser for IPFIX file formats"
  :url "http://www.github.com/brennonyork/ipfix-reader"
  :license {:name "MIT"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[gloss "0.2.2-SNAPSHOT"]
                 [org.clojure/clojure "1.5.1"]
                 [org.clojure/clojure-contrib "1.2.0"]]
  :aot :all
  :main ipfix-reader.core)
