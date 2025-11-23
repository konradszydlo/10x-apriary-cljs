(ns com.apriary.schema)

(def schema
  {:user/id :uuid
   :user [:map {:closed true}
          [:xt/id                     :user/id]
          [:user/email                :string]
          [:user/joined-at            inst?]]

   :generation/id :uuid
   :generation [:map {:closed true}
                [:xt/id                              :uuid]
                [:generation/id                      :uuid]
                [:generation/user-id                 :uuid]
                [:generation/model                   :string]
                [:generation/generated-count         [:int {:min 0}]]
                [:generation/accepted-unedited-count [:int {:min 0}]]
                [:generation/accepted-edited-count   [:int {:min 0}]]
                [:generation/duration-ms             [:int {:min 0}]]
                [:generation/created-at              inst?]
                [:generation/updated-at              inst?]]

   :summary/id :uuid
   :summary [:map {:closed true}
             [:xt/id                     :uuid]
             [:summary/id                :uuid]
             [:summary/user-id           :uuid]
             [:summary/generation-id {:optional true} [:maybe :uuid]]
             [:summary/source            [:enum :ai-full :ai-partial :manual]]
             [:summary/created-at        inst?]
             [:summary/updated-at        inst?]
             [:summary/hive-number {:optional true} [:maybe :string]]
             [:summary/observation-date {:optional true} [:maybe :string]]
             [:summary/special-feature {:optional true} [:maybe :string]]
             [:summary/content           :string]]})

(def module
  {:schema schema})
