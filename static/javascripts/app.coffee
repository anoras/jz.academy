$ ->
    class AppRouter extends Support.SwappingRouter
        routes:
            '':                     'index'
            'contacts/new':         'new'
            'contacts/:id':         'show'
            'contacts/:id/edit':    'edit'

        initialize: (options) ->
            @el = options.el
            @contacts = options.collection

        index: ->
            view = new IndexView(listView: new ContactListView(collection: @contacts))
            @swap(view)
        show: (id) ->
            @contacts.fetch
                success: =>
                    contact = @contacts.get(id)
                    view = new IndexView
                        listView: new ContactListView(collection: @contacts),
                        detailView: new ShowContactView(model: contact)
                    @swap(view)
        new: ->
            view = new IndexView
                        listView: new ContactListView(collection: @contacts),
                        detailView: new NewOrEditContactView(collection: @contacts)
            @swap(view)

        edit: (id) ->
            contact = @contacts.get(id)
            view = new IndexView
                listView: new ContactListView(collection: @contacts),
                detailView: new NewOrEditContactView(collection: @contacts, model: contact)
            @swap(view)


    class IndexView extends Support.CompositeView
        initialize: (options) ->
            @listView = options.listView
            @detailView = options.detailView
        render: ->
            @$el.html(Handlebars.templates.index())
            if (@listView?)
                @renderChild(@listView)
                @$('#list-view').html(@listView.el)
            if (@detailView?)
                @renderChild(@detailView)
                @$('#detail-view').html(@detailView.el)
            @

    class ContactListView extends Support.CompositeView
        initialize: ->
            @collection.on('add destroy', @render)
        render: =>
            @$el.html(Handlebars.templates.contact_list(contacts: @collection.toJSON()))
            @

    class ShowContactView extends Support.CompositeView
        render: ->
            @$el.html(Handlebars.templates.show_contact(@model.toJSON()))
            @

    class NewOrEditContactView extends Support.CompositeView
        events:
            'submit':                       'save'
            'click #delete-contact-btn':    'delete'
        initialize: ->
            @model ?= new Contact
        render: ->
            @form = new Backbone.Form(model: @model)
            @$el.append(@form.render().el)
            @form.$el.append(Handlebars.templates.form_buttons(@model.toJSON()))
            @
        save: (e) ->
            e.preventDefault()
            @form.commit()
            @form.model.save({},
                success: (model, response) =>
                    @collection.add(model)
                error: (model, response) ->
                    alert("Something went wrong")
            )

        delete: ->
            @model.destroy()

    class Contact extends Backbone.Model
        urlRoot: '/contacts'
        idAttribute: '_id'
        schema:
            'firstName':    'Text'
            'lastName':     'Text'
            'email':        'Text'

    class ContactCollection extends Backbone.Collection
        model: Contact
        url: '/contacts'

    contacts = new ContactCollection(JSON.parse($('#contacts-json').html()))
    router = new AppRouter(collection: contacts ,el: $('div#app'))
    Backbone.history.start() unless Backbone.history.started

    eventbus = new vertx.EventBus("http://0.0.0.0:1337/eventbus")
    eventbus.onopen = =>
        eventbus.registerHandler('contacts', (msg, replyTo) ->
            alert(JSON.stringify(msg))
        )

 # curl -v -H "Accept: application/json" -H "Content-type: application/json" -X POST -d
 # ' {"user":{"first_name":"firstname","last_name":"lastname","email":"email@email.com","password":"app123","password_confirmation":"app123"}}'
 # http://localhost:3000/api/1/users