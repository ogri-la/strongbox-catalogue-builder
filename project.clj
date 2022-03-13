(defproject scb "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [clj-http "3.12.3"]
                 [org.clojure/tools.namespace "1.2.0"]

                 [gui-diff "0.6.7" :exclusions [net.cgrant/parsley
                                                org.flatland/ordered]] ;; pops up a graphical diff for test results
                 [org.flatland/ordered "1.5.9"] ;; gui-diff bumped transitive dependency
                 [clj-commons/fs "1.6.310"]
                 [clj-http-fake "1.0.3"] ;; fake http responses for testing
                 
                 [com.taoensso/timbre "5.1.2"]
                 ]
  
  :managed-dependencies [[org.flatland/ordered "1.5.9"]
                         ;; fixes the annoying:
                         ;; "WARNING: cat already refers to: #'clojure.core/cat in namespace: net.cgrand.parsley.fold, being replaced by: #'net.cgrand.parsley.fold/cat"
                         ;; https://github.com/cgrand/parsley/issues/15
                         ;; see `gui-diff` exclusion
                         [net.cgrand/parsley "0.9.3"]]
  :repl-options {:init-ns scb.user}

  ;;:profiles {:user {:plugins [[venantius/yagni "0.1.7"]]}}
  :plugins [[jonase/eastwood "0.9.9"]
            [lein-cljfmt "0.8.0"]
            ]

  :eastwood {:exclude-linters [:constant-test
                               ;;:reflection
                               ]
             ;; linters that are otherwise disabled
             :add-linters [:unused-namespaces
                           :unused-private-vars
                           ;;:unused-locals ;; prefer to keep for readability
                           ;;:unused-fn-args ;; prefer to keep for readability
                           ;;:keyword-typos ;; bugged with spec?
                           ]
             :only-modified true
             }
  
  )
