(defproject thosmos/mysql-to-datomic "0.3.0"
  :description "MySQL-to-Datomic"
  :url "https://github.com/thosmos/mysql-to-datomic"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  ;:dependencies [[org.clojure/clojure "1.8.0"]
  ;
  ;               [org.clojure/java.jdbc "0.7.3"]
  ;               [mysql/mysql-connector-java "5.1.44"]
  ;               [hikari-cp "1.8.1"]
  ;
  ;               [com.rpl/specter "1.0.4"]
  ;
  ;               [com.datomic/datomic-pro "0.9.5661" :exclusions [org.slf4j/slf4j-nop]]
  ;
  ;               [datomic-schema "1.3.0"]
  ;               [io.rkn/conformity "0.5.1"]
  ;               ;[brambling "0.0.3"]
  ;
  ;               [thosmos/util "0.1.3"]
  ;
  ;               [environ "1.1.0"]
  ;               [org.clojure/tools.logging "0.4.1"]
  ;               [ch.qos.logback/logback-classic "1.2.3" :exclusions [org.slf4j/slf4j-api]]
  ;               ;[org.slf4j/jul-to-slf4j "1.7.22"]
  ;               ;[org.slf4j/jcl-over-slf4j "1.7.22"]
  ;               ;[org.slf4j/log4j-over-slf4j "1.7.22"]
  ;               ]


  :repositories {"my.datomic.com" {:url "https://my.datomic.com/repo"
                                   :username :env/datomic_username
                                   :password :env/datomic_password}}

  :plugins [;[lein-ancient "0.6.14"]
            [lein-tools-deps "0.4.1"]
            ;[lein-environ "1.1.0"]
            ;[gorillalabs/lein-docker "1.5.0"]
            ]

  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config {:config-files [:install :user :project]}


  :uberjar-name "mysql-to-datomic.jar"
  ;:min-lein-version "2.7.0"
  :resource-paths ["config", "resources"]

  :docker {:image-name "thosmos/mysql-to-datomic"
           :tags ["%s" "latest"] ; %s will splice the project version into the tag
           :dockerfile "Dockerfile"
           :build-dir  "."}

  :profiles { :uberjar {:aot [mysql-to-datomic.core]} }
  :main ^{:skip-aot true} mysql-to-datomic.core

  ;; -Xms128m -Xmx370m -Ddatomic.objectCacheMax=128m -server
  :jvm-opts ^:replace ["-Xms128m" "-Xmx370m" "-Ddatomic.objectCacheMax=128m" "-server"]

  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag"]
                  ["clean"]
                  ["uberjar"]
                  ["deploy"]
                  ["docker" "build"]
                  ["docker" "push"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]
  )
