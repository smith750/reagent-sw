(ns reagent-sw.core
    (:require [reagent.core :as reagent :refer [atom]]
              [reagent.session :as session]
              [secretary.core :as secretary :include-macros true]
              [goog.events :as events]
              [goog.history.EventType :as EventType]
			  [ajax.core :as ajax])
    (:import goog.History))

;; -------------------------
;; Models

(defonce starships (reagent/atom []))

(defonce starship-details (reagent/atom {}))
	
(defonce pilot-details (reagent/atom {}))

;; -------------------------
;; Utilities
(defn name-to-label [s]
	(-> s
		(clojure.string/replace #"_" " ")
		clojure.string/capitalize))
	
(defn detail-columns [keys]
	(filter #(not (some #{%} '("films" "created" "edited" "url" "homeworld" "species" "vehicles" "starships"))) keys))
	
(defn load-starships [url]
	(ajax/GET url 
		{
			:handler (fn [response] 
				(do 
					(swap! starships concat (get response "results")) 
					(cond 
						(get response "next") (load-starships (get response "next")))) ) 
			:error-handler #(js/console.error "could not load data")
		}))

(defn load-detail [url data-atom]
	(ajax/GET url
		{
			:handler (fn [response] 
				(js/console.log (str "Read " url " and got " response))
				(swap! data-atom assoc url response))
			:error-handler #(js/console.error "could not load data")
		}
		)
	)
	
(defn retrieve-detail [url data-atom]
	(do
		(cond 
			(not (contains? @data-atom url)) (load-detail url data-atom))
		(get @data-atom url)))
		
(defn build-starship-url [starship-id]
	(str "http://swapi.co/api/starships/" starship-id "/"))
	
(defn build-pilot-url [pilot-id]
	(str "http://swapi.co/api/people/" pilot-id "/"))

(defn retrieve-starship [starship-id]
	(js/console.log (str "Retrieving " starship-id))
	(retrieve-detail (build-starship-url starship-id) starship-details))
	
(defn retrieve-pilot [pilot-id]
	(retrieve-detail (build-pilot-url pilot-id) pilot-details))
	
(defn parse-sw-api-id [url object-name]
	(nth (re-find (re-pattern (str "http://swapi.co/api/" object-name "/(\\d+)/")) url) 1))
	
(defn parse-starship-id [url]
	(parse-sw-api-id url "starships"))
	
(defn parse-pilot-id [url]
	(parse-sw-api-id url "people"))

;; -------------------------
;; Views

(defn current-page []
  [:div [(session/get :current-page)]])

(defn starships-row [starship]
	(let [starship-id (parse-starship-id (get starship "url"))
	      starship-name (get starship "name")]
	[:tr {:key (str "starship-" starship-id)}
		[:td [:a {:href (str "#/starship?id=" starship-id)} starship-name]]
		[:td (get starship "model")]
	])
)

(defn starships-table [starships]
	(let [starship-rows (map #(starships-row %) starships)]
	[:table
		[:tr
			[:th "Name"]
			[:th "Model"]
		]
		starship-rows
	])
)

(defn home-page []
	(reagent/create-class {
		:componentWillMount #(load-starships "http://swapi.co/api/starships/")
		:display-name "starships list"
		:reagent-render (fn [] (starships-table @starships))
	})
)

(defn build-detail-key [detail-name column]
	{:key (str detail-name "-" column)})

(defn normal-detail-row [detail column detail-name]
	(let [label (name-to-label column)
		  data (get detail column)]
		[:tr (build-detail-key detail-name column) [:th label] [:td data]]))
		
(defn pilot-element [pilot-url]
	(let [pilot-id (parse-pilot-id pilot-url)
		  pilot (retrieve-pilot pilot-id)]
		[:li {:key (str "pilot-" pilot-id)} [:a {:href (str "#/pilot?id=" pilot-id)} (get pilot "name")]]))
		
(defn pilot-detail-row [detail column detail-name]
	(let [label "Pilots"
		  data (map #(pilot-element %) (get detail "pilots"))]
		[:tr (build-detail-key detail-name column) [:th label] [:td data]]))
		
(defn detail-row [column]
	(if (= column "pilots")
		pilot-detail-row
		normal-detail-row))

(defn detail-page [detail detail-name]
	(js/console.log (str "detail page detail " detail))
	(let [rows (map #((detail-row %) detail % detail-name) (detail-columns (keys detail)))]
	    (js/console.log (str "detail page rows " rows))
		[:div [:table rows] [:div [:a {:href "/"} "Back to Starships"]]]
		)
	)

(defn starship-page []
	(js/console.log (str "starship page " (session/get :starship-id)))
	(reagent/create-class {
		:componentWillMount #(retrieve-starship (session/get :starship-id))
		:display-name "starship detail"
		:reagent-render (fn [] (detail-page (get @starship-details (build-starship-url (session/get :starship-id))) "starships"))
	}))
	
(defn pilot-page []
	(js/console.log (str "pilot page " (session/get :pilot-id)))
	(reagent/create-class {
		:componentWillMount #(retrieve-pilot (session/get :pilot-id))
		:display-name "pilot detail"
		:reagent-render (fn [] (detail-page (get @pilot-details (build-pilot-url (session/get :pilot-id))) "people"))
	}))	


;; -------------------------
;; Routes

(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (session/put! :current-page #'home-page))

(secretary/defroute "/starship" [query-params]
  (js/console.log (str "routed to starship " (:id query-params)))
  (session/put! :starship-id (:id query-params))
  (session/put! :current-page #'starship-page))

(secretary/defroute "/pilot" [query-params]
  (js/console.log (str "routed to pilot " (:id query-params)))
  (session/put! :pilot-id (:id query-params))
  (session/put! :current-page #'pilot-page))

;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     EventType/NAVIGATE
     (fn [event]
       (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

;; -------------------------
;; Initialize app
(comment
(defn mount-root []
  (reagent/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (hook-browser-navigation!)
  (mount-root))
)

(defn init! []
	(hook-browser-navigation!)
	(reagent/render [:div [:h3 "Reagent Star Wars"] [current-page]] (.getElementById js/document "app")))
