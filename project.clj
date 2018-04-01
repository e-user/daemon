(defproject daemon "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [aleph "0.4.4"]
                 [compojure "1.6.0"]
                 [com.taoensso/timbre "4.10.0"]
                 [com.fzakaria/slf4j-timbre "0.3.8"]]
  :source-paths ["src/clj"]
  :test-paths ["test/clj"]
  :main ^:skip-aot daemon.core
  :profiles {:uberjar {:aot :all}})
