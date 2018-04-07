(defproject daemon "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Apache License Version 2.0"
            :url "https://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [aleph "0.4.4"]
                 [aleph-middleware "0.2.0"]
                 [compojure "1.6.0"]
                 [com.taoensso/timbre "4.10.0"]
                 [com.fzakaria/slf4j-timbre "0.3.8"]
                 [cheshire "5.8.0"]]
  :source-paths ["src/clj"]
  :test-paths ["test/clj"]
  :main daemon.core
  :profiles {:uberjar {:aot :all}}
  :aot [daemon.core])
