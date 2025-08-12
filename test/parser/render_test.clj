(ns parser.render-test
  (:require
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [jj.majavat.parser :as parser]
            [jj.majavat.renderer :as renderer])
  (:import (java.io InputStream)))


(defn- crlf->lf [s]
  (str/replace s "\r\n" "\n"))

(deftest prerender-test
  (is (= [{:type :text :value "hello world"}
          {:type :value-node :value :not-existing}]
         (renderer/pre-render [{:type :text :value "hello "}
                               {:type :value-node :value :name}
                               {:type :value-node :value :not-existing}]
                              {:name "world"}))))


(deftest prerender-empty-context-test
  (is (= [{:type :text :value "hello "}
          {:type :value-node :value :name}
          {:type :value-node :value :age}]
         (renderer/pre-render [{:type :text :value "hello "}
                               {:type :value-node :value :name}
                               {:type :value-node :value :age}]
                              {}))))

(deftest prerender-no-text-nodes-test
  (is (= [{:type :value-node :value :missing}
          {:type :value-node :value :also-missing}]
         (renderer/pre-render [{:type :value-node :value :name}
                               {:type :value-node :value :missing}
                               {:type :value-node :value :age}
                               {:type :value-node :value :also-missing}]
                              {:name "Alice" :age 30}))))

(deftest prerender-all-values-exist-test
  (is (= [{:type :text :value "Hello Alice, You are 30"}]
         (renderer/pre-render [{:type :text :value "Hello "}
                               {:type :value-node :value :name}
                               {:type :text :value ", You are "}
                               {:type :value-node :value :age}]
                              {:name "Alice" :age 30}))))

(deftest prerender-mixed-node-types-test
  (is (= [{:type :text :value "Department: Engineering"}
          {:type :for :identifier :employee :source [:dept :employees]}
          {:type :value-node :value :missing-budget}
          {:type :if :condition [:dept :active]}]
         (renderer/pre-render [{:type :text :value "Department: "}
                               {:type :value-node :value :name}
                               {:type :for :identifier :employee :source [:dept :employees]}
                               {:type :value-node :value :missing-budget}
                               {:type :if :condition [:dept :active]}]
                              {:name "Engineering" :budget "$500K"}))))

(deftest prerender-non-string-text-values-test
  (is (= [{:type  :text
           :value "42[1 2 3]"}]
         (renderer/pre-render [{:type :text :value 42}
                               {:type :text :value nil}
                               {:type :text :value [1 2 3]}]
                              {:name "test"}))))

(deftest prerender-special-characters-test
  (is (= [{:type :text :value "🎉 Hello 世界!"}
          {:type :value-node :value :missing}]
         (renderer/pre-render [{:type :text :value "🎉 Hello "}
                               {:type :value-node :value :greeting}
                               {:type :text :value "!"}
                               {:type :value-node :value :missing}]
                              {:greeting "世界"}))))

(deftest prerender-nested-context-keys-test
  (is (= [{:type :text :value "User: admin"}
          {:type :value-node :value :role}                  ; This should remain since we only check top-level
          {:type :value-node :value :missing}]
         (renderer/pre-render [{:type :text :value "User: "}
                               {:type :value-node :value :username}
                               {:type :value-node :value :role} ; nested key, should remain
                               {:type :value-node :value :missing}]
                              {:username "admin"
                               :profile  {:role "administrator"}}))))

(deftest prerender-empty-instructions-test
  (is (= [] (renderer/pre-render [] {:name "test"}))))

(deftest prerender-keyword-vs-string-keys-test
  (is (= [{:type :text :value "Hello "}
          {:type :value-node :value :name}                  ; keyword not found in string-key context
          {:type :value-node :value :missing}]
         (renderer/pre-render [{:type :text :value "Hello "}
                               {:type :value-node :value :name}
                               {:type :value-node :value :missing}]
                              {"name" "Alice"}))))          ; string key, not keyword

(defn assert-render [template context expected-string]
  (is (= (crlf->lf expected-string)
         (crlf->lf (renderer/render template context))) "string assertion")
  (is (= (crlf->lf expected-string)
         (crlf->lf (String. (.readAllBytes ^InputStream (renderer/render-to-input-stream-streaming template context))))
         ) "input stream assertion"))

(deftest advanced-test
  (let [context {:company {:departments [{:name      "Engineering"
                                          :budget    "$500K"
                                          :employees [{:name       "Alice Johnson"
                                                       :title      "Senior Developer"
                                                       :is_manager true}
                                                      {:name       "Bob Smith"
                                                       :title      "Junior Developer"
                                                       :is_manager false}]}
                                         {:name      "Marketing"
                                          :budget    "$300K"
                                          :employees [{:name       "Carol Davis"
                                                       :title      "Marketing Manager"
                                                       :is_manager true}]}]}}
        template [{:type       :for
                   :identifier :department
                   :source     [:company :departments]
                   :body       [{:type :text :value "Department: "}
                                {:type :value-node :value [:department :name]}
                                {:type :text :value " (Budget: "}
                                {:type :value-node :value [:department :budget]}
                                {:type :text :value ")\n"}
                                {:type       :for
                                 :identifier :employee
                                 :source     [:department :employees]
                                 :body       [{:type       :if
                                               :condition  [:employee :is_manager]
                                               :when-true  [{:type :text :value "👔 MANAGER: "}
                                                            {:type :value-node :value [:employee :name]}
                                                            {:type :text :value " - "}
                                                            {:type :value-node :value [:employee :title]}
                                                            {:type :text :value "\n"}
                                                            {:type       :for
                                                             :identifier :report
                                                             :source     [:employee :direct_reports]
                                                             :body       [{:type :text :value "    └─ "}
                                                                          {:type :value-node :value [:report :name]}
                                                                          {:type :text :value " ("}
                                                                          {:type :value-node :value [:report :role]}
                                                                          {:type :text :value ")\n"}]}]
                                               :when-false [{:type :text :value "👤 "}
                                                            {:type :value-node :value [:employee :name]}
                                                            {:type :text :value " - "}
                                                            {:type :value-node :value [:employee :title]}
                                                            {:type :text :value "\n"}]}]}
                                {:type :text :value "\n"}]}]
        expected-string "Department: Engineering (Budget: $500K)
👔 MANAGER: Alice Johnson - Senior Developer
👤 Bob Smith - Junior Developer

Department: Marketing (Budget: $300K)
👔 MANAGER: Carol Davis - Marketing Manager

"]
    (assert-render template context expected-string)))




(deftest test-inheritance
  (let [expected-string "hello jj from parent header

\"testing your email is: some@mail.com\"
foobarbaz
this is a  footer"
        template (parser/parse "inheritance-test")
        context {:user {:name "jj"
                        :email "some@mail.com"}}]
    (assert-render template context expected-string)))


(deftest test-not-existing-file
  (let [expected-string "not-existing-file resource can not be found."
        template (parser/parse "not-existing-file")
        context {}]
    (assert-render template context expected-string)))

(deftest include-not-existing
  (let [expected-string "not-existing-include-file does not exist"
        template (parser/parse "includes-not-existing-test")
        context {}]
    (assert-render template context expected-string)))


(deftest extends-not-existing-file
  (let [expected-string "./not-existing-file does not exist"
        template (parser/parse "extends-not-existing-test")
        context {}]
    (assert-render template context expected-string)))