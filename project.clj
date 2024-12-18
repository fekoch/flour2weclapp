(defproject flour2weclapp "0.1.1"
  :description "Sync new documents from flour to weclapp"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [clj-http "3.12.3"]
                 [ring "1.12.1"]
                 [cheshire "5.13.0"]]
  :main ^:skip-aot flour2weclapp.core
  :target-path "target/%s"
  :uberjar-name "flour2weclapp-0.1.1-standalone.jar"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
