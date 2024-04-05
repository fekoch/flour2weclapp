(ns flour2weclapp.core
  (:require [clj-http.client :as client]
            )
  (:gen-class)
  (:import (java.time Duration Instant)
           ))


(defn target-flour [path]
  "Create a https url from the path using API_HOST and API_BASE env vars"
  (let [api-host (System/getenv "FLOUR_API_HOST")
        api-base (System/getenv "FLOUR_API_BASE")]
    (if (empty? api-host)
      (throw (Exception. "FLOUR_API_HOST not set")))
    (if (empty? api-base)
      (throw (Exception. "FLOUR_API_BASE not set")))
    (str "https://" api-host api-base path)
    )
  )

(defn get-documents
  []
  "Get all invoice-data from flour"
  (let [date-after (.toString (.minus (Instant/now) (Duration/ofDays 1)))
        response (client/get (target-flour "/documents")
                             {
                              :query-params  {:dateAfter [date-after]}
                              :oauth-token   (System/getenv "FLOUR_TOKEN")
                              :accept        :json
                              :cache-control "no-cache"
                              :cookie-policy :none
                              :as            :json
                              })]
    (:body response)
    )
  )

(defn target-weclapp [path]
  "Create a weclapp api url from the path using WECLAPP_TENANT_ID env var"
  (let [tenant (System/getenv "WECLAPP_TENANT_ID")]
    (if (empty? tenant)
      (throw (Exception. "WECLAPP_TENANT_ID not set")))
    (str "https://" tenant ".weclapp.com/webapp/api/v1" path)
    )
  )

(defn lookup-weclapp-id [flour-article]
  "Get the weclapp articleId for a given flour article"
  (let [
        query (cond
                (seq (flour-article :ean)) {:ean-eq [(flour-article :ean)]}
                ; NOTE: if :number is the variant article number this will still fail
                (seq (flour-article :number)) {:articleNumber-eq [(flour-article :number)]}
                )
        response (client/get (target-weclapp "/article")
                             {
                              :query-params  query
                              :headers       {"AuthenticationToken" (System/getenv "WECLAPP_TOKEN")}
                              :cache-control "no-cache"
                              :cookie-policy :none
                              :as            :json
                              }
                             )
        article-id (-> response :body :result first :id)
        ]
    (when-not article-id
      (throw (Exception. (str "Article not found in weclapp: " (select-keys flour-article [:number :ean :_id :title]))))
      )
    article-id
    )
  )

(defn transform-order-items [order-items]
  "Transform flour order items to weclapp items"
  (map (fn [item]
         {
          :articleId (lookup-weclapp-id item)
          :quantity  (item :amount)
          }
         )
       order-items)
  )

(defn create-warehouseStockMovement [movementNote articleId quantity sourceStoragePlaceId]
  (let [movement-data {
                       :movementNote         movementNote
                       :articleId            articleId
                       :quantity             quantity
                       :sourceStoragePlaceId sourceStoragePlaceId
                       }
        response (client/post (target-weclapp "/warehouseStockMovement/bookOutgoingMovement")
                              {
                               :form-params           movement-data
                               :content-type          :json
                               :as                    :json
                               :headers               {"AuthenticationToken" (System/getenv "WECLAPP_TOKEN")}
                               :cache-control         "no-cache"
                               :cookie-policy         :none
                               :throw-entire-message? true
                               }
                              )]
    response
    )
  )


(defn check-already-synced [document-number]
  "Check if the document has already been synced to weclapp"
  (let [response (client/get (target-weclapp "/warehouseStockMovement/count")
                             {
                              :query-params  {:movementNote-eq [document-number]}
                              :headers       {"AuthenticationToken" (System/getenv "WECLAPP_TOKEN")}
                              :cache-control "no-cache"
                              :cookie-policy :none
                              :as            :json
                              }
                             )]
    (-> response :body :result (not= 0))
    )
  )

(defn process-document [document sourceStoragePlaceId]
  "Adds a warehouseStockMovement for each item in the document into weclapp, if it hasn't been synced yet"
  (try
    (let [order-items (document :items)
          items (transform-order-items order-items)]
      (if-not (check-already-synced (document :number))
        ((doseq [item items]
           ;; NOTE: If this breaks, the document will be partly synced
           (create-warehouseStockMovement (document :number)
                                          (item :articleId)
                                          (item :quantity)
                                          sourceStoragePlaceId)
           )
         (println (str "Document " (document :number) " synced successfully"))
         )
        )
      (println (str "Document " (document :number) " already (at least partly) synced")))
    (catch Exception e (.println *err* (str "Error syncing document " (document :number) ": " e)))
    ))

(defn -main
  [& _]
  (let [sourceStoragePlaceId (System/getenv "WECLAPP_STORAGE_PLACE_ID")]
    (if-not sourceStoragePlaceId
      (throw (Exception. "WECLAPP_STORAGE_PLACE_ID not set")))
    (let [documents (filter #(= (:type %) "R") (get-documents))]
      (doseq [document documents]
        (process-document document sourceStoragePlaceId)
        )
      )
    )
  )
