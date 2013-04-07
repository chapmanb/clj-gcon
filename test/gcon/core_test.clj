(ns gcon.core-test
  "Testing for generic integration with remote genomic APIs.
   To setup for various environments, requires parameters set as
   environmental variables or in a lein profile:
   https://github.com/weavejester/environ#usage
   - Required parameters for GenomeSpace:
     gs-username, gs-password
   - Required paramters for Galaxy:
     galaxy-url, galaxy-apikey"
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [environ.core :as environ]
            [gcon.core :as gcon]))

(deftest genomespace-files
  (testing "Authentication, push and pull from GenomeSpace."
    (let [{:keys [gs-username gs-password gs-url]} environ/env]
      (when (and gs-username gs-password)
        (let [rclient (gcon/get-client {:type :gs
                                        :username gs-username
                                        :password gs-password
                                        :url gs-url})
              dirs (gcon/list-dirs rclient ".")
              files (gcon/list-files rclient (first dirs) :vcf)]
          (println (first files)))))))

(deftest galaxy-files
  (testing "Authentication, push and pull from Galaxy."
    (let [{:keys [galaxy-url galaxy-apikey]} environ/env]
      (when (and galaxy-url galaxy-apikey)
        (let [rclient (gcon/get-client {:type :galaxy
                                        :url galaxy-url
                                        :api-key galaxy-apikey})
              dirs (gcon/list-dirs rclient nil)
              files (gcon/list-files rclient (first dirs) :vcf)]
          (println (first files)))))))
