(defproject thosmos/mysql-to-datomic "0.3.3"
  :description "MySQL-to-Datomic"
  :url "https://github.com/thosmos/mysql-to-datomic"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :repositories {"my.datomic.com" {:url "https://my.datomic.com/repo"
                                   :username :env/datomic_username
                                   :password :env/datomic_password}}


  :plugins [[lein-tools-deps "0.4.1"]]

  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config {:config-files [:install :user :project]}

  :uberjar-name "mysql-to-datomic.jar"
  :resource-paths ["config", "resources"]

  ;:docker {:image-name "thosmos/mysql-to-datomic"
  ;         :tags ["%s" "latest"] ; %s will splice the project version into the tag
  ;         :dockerfile "Dockerfile"
  ;         :build-dir  "."}

  :profiles { :uberjar {:aot [mysql-to-datomic.core]}}
  :main ^{:skip-aot true} mysql-to-datomic.core

  ;; -Xms128m -Xmx370m -Ddatomic.objectCacheMax=128m -server
  :jvm-opts ^:replace ["-Xms128m" "-Xmx370m" "-Ddatomic.objectCacheMax=128m" "-server"]

  :deploy-repositories [["releases" :clojars]]

  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag"]
                  ;["clean"]
                  ;["uberjar"]
                  ["deploy"]
                  ;["docker" "build"]
                  ;["docker" "push"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]])

