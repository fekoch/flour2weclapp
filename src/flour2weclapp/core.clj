(ns flour2weclapp.core
  (:require [clj-http.client :as client]
            )
  (:gen-class)
  (:import (java.time Duration Instant)
           ))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  ;; TODO: fetch new documents and create shipments for each
  (println "Hello, World!"))

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
  (let [date-after (.toString (.minus (Instant/now) (Duration/ofDays 365))) ;; TODO: get only new documents
        response (client/get (target-flour "/documents")
                             {
                              ;:query-params  {:dateAfter [date-after]}
                              :oauth-token   (System/getenv "FLOUR_TOKEN")
                              :accept        :json
                              :cache-control "no-cache"
                              :cookie-policy :none
                              :as            :json
                              })]
    (:body response)
    ; TODO: Filter for only type "R"
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

(defn lookup-weclapp-id [ean-number]
  "Get the weclapp articleId for a given ean number"
  (let [response (client/get (target-weclapp "/article")
                             {
                              :query-params  {:ean-eq [ean-number]}
                              :headers       {"AuthenticationToken" (System/getenv "WECLAPP_TOKEN")}
                              :cache-control "no-cache"
                              :cookie-policy :none
                              :as            :json
                              }
                             )]
    (-> response :body :result first :id)
    ))

(defn transform-order-items [order-items]
  "Transform flour order items to weclapp shipment items"
  (map (fn [item]
         {
          :articleId (lookup-weclapp-id (item :ean))
          :quantity      (item :amount)
          }
         )
       order-items)
  )


(defn create-shipment [order customerId warehouseId salesChannel]
  "Create a new shipment in weclapp using POS_$number as the shipment number."
  (let [
        item-data (transform-order-items (order :items))
        shipment-data {
                       :shipmentNumber   (str "POS_" (order :number))
                       :recipientPartyId customerId
                       :status           "NEW" ;; TODO: ask Ben if there is a better status
                       :shipmentType     "STANDARD" ;; TODO: ask Ben if there is a better type
                       :salesChannel     salesChannel
                       :warehouseId      warehouseId
                       :shipmentItems    item-data
                       }
        response (client/post (target-weclapp "/shipment")
                              {
                               :form-params   shipment-data
                               :content-type  :json
                               :headers       {"AuthenticationToken" (System/getenv "WECLAPP_TOKEN")}
                               :cache-control "no-cache"
                               :cookie-policy :none
                               :throw-entire-message? true
                               :debug true ;; FIXME: remove debug after testing
                               :debug-body true
                               }
                              )]
    response
    )
  )




;; test document with edited in ean number for the product TODO: test after flour has ingested ean numbers
(def test-document {:description     "",
                    :terminal        {:receipt []},
                    :tags            [],
                    :_id             "660ee3169159b70727efc82b",
                    :payments        [{:amount  10,
                                       :_id     "660ee3162899b0075c84c46c",
                                       :paidAt  "2024-04-04T17:27:50.499Z",
                                       :ref     "6603ea106fc9202ef7262069",
                                       :history [],
                                       :info3   "",
                                       :source  "",
                                       :info1   "",
                                       :details nil,
                                       :status  "",
                                       :locked  false,
                                       :info2   ""}],
                    :dsfinvktxID     nil,
                    :isVoided        false,
                    :tseTx           "660ee2fe5537d16b9abf42df",
                    :date            "2024-04-04T17:27:50.594Z",
                    :updatedAt       "2024-04-04T17:27:50.600Z",
                    :group           "6603ea116fc9202ef726252e",
                    :meta            {:foreignNumber "", :origin "", :info1 ""},
                    :sentbymail      false,
                    :prints          [],
                    :secucard        {:receipt nil},
                    :number          "13001000003",
                    :completed       false,
                    :client          "65fbe2454f02853f4dacef35",
                    :pos             {:cachier           "",
                                      :posjournal        "6603ea248e51c5287d8a9284",
                                      :transaction       "660ee3165537d16b9abf5517",
                                      :posbasket         "660ee3119159b70727efc828",
                                      :moneyBack         nil,
                                      :transactionNumber 16,
                                      :moneyPaid         10,
                                      :pospayment        "6603ea106fc9202ef7262300",
                                      :pointofsale       "6603ea106fc9202ef7262521"},
                    :createdAt       "2024-04-04T17:27:50.594Z",
                    :type            "R",
                    :info3           "",
                    :isVoid          false,
                    :logId           "50d0750f-8fa3-4bac-beb8-d10fd37eedc4",
                    :tax             [{:incVat 10, :exVat 8.4, :tax 1.6, :rate 19, :_id "660ee30fd5cb8207518ac169"}],
                    :paymentStatus   "succeeded",
                    :businesspartner "6603ea106fc9202ef7262250",
                    :paymentMethod   nil,
                    :currency        {:ref "65fbe2454f02853f4dace078", :symbol "€", :iso "EUR", :factor 1},
                    :info1           "",
                    :taxType         "",
                    :prepayment      false,
                    :project         nil,
                    :status          {:processed false, :deleted false, :visible true},
                    :totalIncVat     10,
                    :__v             0,
                    :credits         [],
                    :de              {:client_serial_number "6603ea106fc9202ef7262521",
                                      :time_start           "2024-04-04T17:27:24.000Z",
                                      :number               15,
                                      :tss_serial_number    "61c0e79fcf699070b71b0087bdde8d0a046e64b046e4eb7e4672c4f4d654df13",
                                      :signature            {:value      "F8J946cUTcKZe2gx7BlxmYDGKHnbTXTmKY5hPRsDJRd72/0m2V6ogRdbwrQxbPDdEAZtlPuNxKRDirte9v0vdw==",
                                                             :algorithm  "ecdsa-plain-SHA256",
                                                             :counter    199,
                                                             :public_key "BImHc2aIsFZZ+mcQMW9PzXIKRI8EW5sOyLN+HDsUiNSMAryj7sMP3slYMju97s6J68cfQpYjjE2xq75+iKtwBDk="},
                                      :timestamp_format     "unixTime",
                                      :time_end             "2024-04-04T17:27:49.000Z",
                                      :error                false,
                                      :qr_code_data         "V0;6603ea106fc9202ef7262521;Kassenbeleg-V1;Beleg^10.00_0.00_0.00_0.00_0.00^10.00:Bar;15;199;2024-04-04T17:27:24.000Z;2024-04-04T17:27:49.000Z;ecdsa-plain-SHA256;unixTime;F8J946cUTcKZe2gx7BlxmYDGKHnbTXTmKY5hPRsDJRd72/0m2V6ogRdbwrQxbPDdEAZtlPuNxKRDirte9v0vdw==;BImHc2aIsFZZ+mcQMW9PzXIKRI8EW5sOyLN+HDsUiNSMAryj7sMP3slYMju97s6J68cfQpYjjE2xq75+iKtwBDk=",
                                      :_version             "2.1.9",
                                      :certificate_serial   nil},
                    :locked          true,
                    :at              {:codeEncodeURI "", :code "", :signature "", :payload ""},
                    :paid            false,
                    :inHouse         false,
                    :info2           "",
                    :order           false,
                    :shipping        {:date nil},
                    :totalExVat      8.4,
                    :items           [{:datevCostcentre1     "",
                                       :description          "",
                                       :amount               1,
                                       :userHasChangedPrice  true,
                                       :_id                  "660ee2fd2899b0075c84c44c",
                                       :psydoprice           0,
                                       :serialNumber         "",
                                       :priceref             0,
                                       :title2               "",
                                       :datevCostcentre2     "",
                                       :number               "--AC__ADI__H43965--",
                                       :unit                 1,
                                       :ref                  "66030b9f201860003186a386",
                                       :differentialTaxation false,
                                       :ean                  "194891308663",
                                       :noTax                false,
                                       :type                 "article",
                                       :taxRate              19,
                                       :hasSerialNumber      false,
                                       :hasCustomPrice       true,
                                       :title                "Adidas Halswärmer Snood Dunkelgrau",
                                       :cancelled            false,
                                       :documents            {:R "660ee3169159b70727efc82b"},
                                       :batchNumber          "",
                                       :taxType              "brutto",
                                       :weight               {:type "tare"},
                                       :totalIncVat          10,
                                       :locked               false,
                                       :customPrice          10,
                                       :order                false,
                                       :datevAccountNumber   "44010",
                                       :notice               "",
                                       :available            0,
                                       :totalExVat           8.4,
                                       :datevTaxKey          "3",
                                       :discount             0,
                                       :autoPriceCalc        true,
                                       :stockitementry       "660ee31632939f78a5f4e7cb",
                                       :quantityPerUnit      1,
                                       :price                10,
                                       :marketplace          {:id "", :name ""},
                                       :user                 "660e921aa821511355513dd5",
                                       :printed              false,
                                       :stock                "6603ea106fc9202ef7262604",
                                       :datevTaxAuto         true,
                                       :itemtracker          "660ee3162398253c1bed6209"}],
                    :version         0,
                    :shippingAddress {:zipCode      "",
                                      :company1     "",
                                      :company2     "",
                                      :company3     "",
                                      :city         "",
                                      :firstName    "",
                                      :salutation   "",
                                      :streetNumber "",
                                      :prefix       "",
                                      :street       "",
                                      :vatId        "",
                                      :lastName     "",
                                      :country      ""},
                    :accounted       false,
                    :credit          false,
                    :user            "660e921aa821511355513dd5",
                    :printed         false,
                    :metadata        nil,
                    :billingAddress  {:zipCode      "",
                                      :company1     "Kunde Standard",
                                      :company2     "",
                                      :company3     "",
                                      :city         "",
                                      :firstName    "",
                                      :salutation   "",
                                      :streetNumber "",
                                      :prefix       "",
                                      :street       "",
                                      :vatId        "",
                                      :lastName     "",
                                      :country      ""},
                    :stock           {:default nil},
                    :totalDue        0})

(defn test-create-shipment [] (create-shipment test-document "2005086" "689724" "GROSS5"))
