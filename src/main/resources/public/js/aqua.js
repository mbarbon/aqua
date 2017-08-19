$(function () {
    'use strict';

    ko.options.deferUpdates = true;

    ko.bindingHandlers.enterKey = {
        init: function (element, valueAccessor, allBindings, _, bindingContext) {
            var callback = valueAccessor();
            $(element).keypress(function (event) {
                var keyCode = (event.which ? event.which : event.keyCode);
                if (keyCode === 13) {
                    callback.call(bindingContext.$data);
                    return false;
                }
                return true;
            });
        }
    };
    ko.bindingHandlers.animeRating = {
        init: function (element, valueAccessor, allbindings, _, bindingContext) {
            var defaults = {
                starWidth: '20px'
            };
            var inputOptions = valueAccessor() || {};
            var options = {};
            for (var attrName in defaults) {
                options[attrName] = defaults[attrName];
            }
            for (var attrName in inputOptions) {
                options[attrName] = inputOptions[attrName];
            }
            if (options.onSet) {
                var onSet = options.onSet;
                options.onSet = function onSetWrapper (rating) {
                    onSet.call(null, bindingContext.$data, rating);
                };
            }
            $(element).rateYo(options);
        }
    };

    var time = function time() {
        return new Date().getTime() / 1000;
    };

    var AnimeFilterButton = function AnimeFilterButton(tag, description, show) {
        var self = this;

        self.tag = tag;
        self.description = description;
        self.show = ko.observable(show);
    };
    AnimeFilterButton.prototype.toggleTag = function toggleTag() {
        var self = this;

        self.show(!self.show());
    };
    AnimeFilterButton.prototype.statusDescription = function statusDescription() {
        var self = this;

        return (self.show() ? 'Hide "' : 'Show "') + self.description + '"';
    };

    var showHideTags = [
        new AnimeFilterButton("planned", "plan to watch", true),
        new AnimeFilterButton("franchise", "related", false),
        new AnimeFilterButton("planned-franchise", "related is planned", false)
    ];

    var tagVisibility = {
        'planned-and-franchise': ko.pureComputed(function () {
            return showHideTags[0].show() && showHideTags[1].show()
        }),
        'planned': showHideTags[0].show,
        'franchise': showHideTags[1].show,
        'planned-franchise': showHideTags[2].show,
        'same-franchise': showHideTags[1].show,
        null: true
    };

    var tagDescription = {
        'planned-and-franchise': 'Plan to watch & related',
        'planned': 'Plan to watch',
        'franchise': 'Related to watched anime',
        'planned-franchise': 'Related to planned anime',
        'same-franchise': 'Same franchise',
        null: ''
    };

    var AnimeRecommendation = function AnimeRecommendation(data, showStars) {
        var self = this;

        self.title = data.title;
        self.image = data.image;
        self.animedbId = data.animedbId;
        self.genres = data.genres;
        self.episodes = data.episodes;
        self.franchiseEpisodes = data.franchiseEpisodes;
        self.season = data.season;
        self.tags = data.tags;
        self.showStars = showStars;
    };
    AnimeRecommendation.prototype.malLink = function malLink () {
        var self = this;

        return "http://myanimelist.net/anime/" + self.animedbId;
    };
    AnimeRecommendation.prototype.malLinkClick = function malLinkClick () {
        var self = this;

        ga('send', 'event', {
            eventCategory: 'mal_link',
            eventAction: 'click',
            eventLabel: '' + self.animedbId,
        });

        return true;
    };

    var ManualItem = function ManualItem(data) {
        var self = this;

        self.title = data.title;
        self.image = data.image;
        self.animedbId = data.animedbId;
        self.genres = data.genres;
        self.episodes = data.episodes;
        self.franchiseEpisodes = data.franchiseEpisodes;
        self.season = data.season;
        self.userStatus = data.userStatus;
        self.userRating = data.userRating || 0;
        self.tags = null;
        self.showStars = true;
    };
    ManualItem.prototype.malLink = function malLink () {
        var self = this;

        return "http://myanimelist.net/anime/" + self.animedbId;
    };
    ManualItem.prototype.malLinkClick = function malLinkClick () {
        var self = this;

        ga('send', 'event', {
            eventCategory: 'mal_link',
            eventAction: 'click',
            eventLabel: '' + self.animedbId,
        });

        return true;
    };

    var SearchModel = function SearchModel(localAnimeListModel) {
        var self = this;

        self.searchString = ko.observable();
        self.searchResults = ko.observableArray();
        self.searchString.subscribe(self.performSearch, self);
        self.localAnimeListModel = localAnimeListModel;
        self.tagVisibility = { null: true };
        self.tagDescription = { null: '' };
    };
    SearchModel.prototype.performSearch = function performSearch() {
        var self = this;

        if (self.searchString().length == 0) {
            self.searchResults([]);
            return;
        }

        $.get({
            url: '/autocomplete',
            data: { term: self.searchString() },
            dataType: 'json',
            success: function (data) {
                var res = [];
                for (var i = 0; i < data.length; ++i)
                    res.push(new ManualItem(data[i]));
                self.searchResults(res);
            }
        });
    };
    SearchModel.prototype.addManualRating = function addManualRating(item, rating) {
        var self = this;

        self.localAnimeListModel.addItem(item, rating);
        self.searchString('');
    };

    var LocalAnimeListModel = function LocalAnimeListModel() {
        var self = this;

        self.loadingTimer = 0;
        self.jsonAnimeList = ko.observable();
        self.manualAnimeList = ko.observableArray();
        self.malUserName = ko.observable();
        self.malLoadQueuePosition = ko.observable()
        self.malRefresh = ko.observable(localStorage.getItem('malRefresh'));
        self.malCanRefresh = ko.pureComputed(function () {
            var lastRefresh = self.malRefresh();

            return time() - lastRefresh > 6 * 3600;
        });
        self.isManualList = ko.observable();
        self.isManualEdit = ko.observable();
        self.isMalList = ko.observable();
        self.search = new SearchModel(self);
        self.manualAnimeList.subscribe(self.manualAnimeListUpdated, self);
        self.hasSource = ko.pureComputed(function () {
            return self.isMalList() || self.isManualList();
        });
        self.tagVisibility = { null: true };
        self.tagDescription = { null: '' };
        self.currentGAPage = null;
    };
    LocalAnimeListModel.prototype.loadMalList = function loadMalList() {
        var self = this;
        var callback = function (count) {
            if (count > 15)
                return;
            if (!self.malUserName())
                return;

            self.loadingTimer = setTimeout(callback, 17000, count + 1);
            self.loadMalListRequest();
        };

        if (!self.malUserName())
            return;
        if (self.loadingTimer)
            clearTimeout(self.loadingTimer);
        self.loadingTimer = setTimeout(callback, 500, 0)
    };
    LocalAnimeListModel.prototype.loadMalListRequest = function loadMalListRequest() {
        var self = this;

        $.get({
            'url': '/list/anime/' + self.malUserName(),
            'dataType': 'json',
            success: function (ratedListOrSpinner) {
                if ('queue-position' in ratedListOrSpinner) {
                    self.malLoadQueuePosition(ratedListOrSpinner['queue-position']);
                } else {
                    var jsonAnimeList = JSON.stringify(ratedListOrSpinner);

                    localStorage.setItem('malRated', jsonAnimeList);
                    localStorage.setItem('malUserName', self.malUserName());
                    localStorage.setItem('malRefresh', time());
                    localStorage.setItem('sourceMode', 'mal');
                    localStorage.setItem('lastRecommendationTime', 0);

                    self.malRefresh(localStorage.getItem('malRefresh'));
                    self.malLoadQueuePosition(0);
                    self.isMalList(true);
                    self.isManualList(false);
                    self.isManualEdit(false);
                    self.jsonAnimeList(jsonAnimeList);
                    if (self.loadingTimer)
                        clearTimeout(self.loadingTimer);
                }
            }
        });
    };
    LocalAnimeListModel.prototype.addItem = function addItem(newItem, rating) {
        var self = this;
        var animeList = self.manualAnimeList();

        var found = false;
        for (var i = 0; i < animeList.length; ++i) {
            if (animeList[i].animedbId == newItem.animedbId) {
                animeList[i].userStatus = 2; // completed
                animeList[i].userRating = rating;
                self.manualAnimeList(animeList);
                self.trackLocalAnime(newItem, 'change');
                return;
            }
        }

        newItem.userStatus = 2; // completed
        newItem.userRating = rating;
        self.manualAnimeList.unshift(newItem);
        self.trackLocalAnime(newItem, 'add');
    };
    LocalAnimeListModel.prototype.removeItem = function removeItem(item) {
        var self = this;
        var animeList = self.manualAnimeList();

        for (var i = 0; i < animeList.length; ++i) {
            if (animeList[i].animedbId == item.animedbId) {
                self.manualAnimeList.splice(i, 1);
                self.trackLocalAnime(item, 'remove');
                return;
            }
        }
    };
    LocalAnimeListModel.prototype.initialSetup = function initialSetup() {
        var self = this;

        var currentMode = localStorage.getItem('sourceMode');
        var storedUserName = localStorage.getItem('malUserName');
        var manualAnimeList = localStorage.getItem('manualAnimeList');

        if (currentMode == 'mal' && storedUserName) {
            self.malUserName(storedUserName);
            self.jsonAnimeList(localStorage.getItem('malRated'));
            self.isMalList(true);
        } else if (currentMode == 'manual' && manualAnimeList) {
            var parsedList = JSON.parse(manualAnimeList);
            var newList = [];

            for (var i = 0; i < parsedList.length; ++i)
                newList.push(new ManualItem(parsedList[i]));

            self.manualAnimeList(newList);
            self.isManualList(true);
            self.isManualEdit(false);
        }

        // start tracking the page
        self.trackCurrentPage = ko.computed(self.trackPage.bind(self));
    };
    LocalAnimeListModel.prototype.manualAnimeListUpdated = function () {
        var self = this;

        var rateList = [];
        var manualAnimeList = self.manualAnimeList();

        localStorage.setItem('lastRecommendationTime', 0);
        if (manualAnimeList.length == 0) {
            self.jsonAnimeList('');
            localStorage.setItem('manualAnimeList', '');
            return;
        }

        for (var i = 0, max = manualAnimeList.length; i < max; ++i) {
            var item = manualAnimeList[i];
            rateList.push([item.animedbId, item.userStatus, item.userRating]);
        }
        self.jsonAnimeList(JSON.stringify(rateList));
        localStorage.setItem('manualAnimeList', JSON.stringify(self.manualAnimeList()));
    };
    LocalAnimeListModel.prototype.useManualList = function useManualList() {
        var self = this;

        localStorage.setItem('sourceMode', 'manual');
        self.isManualList(true);
        self.isManualEdit(false);
    };
    LocalAnimeListModel.prototype.resetUserData = function resetUserData() {
        var self = this;

        localStorage.removeItem('sourceMode');
        self.isManualList(false);
        self.isManualEdit(false);
        self.isMalList(false);
        self.malLoadQueuePosition(0);
        self.jsonAnimeList('');
        self.malUserName('');
    };
    LocalAnimeListModel.prototype.editLocalList = function editLocalList() {
        var self = this;

        self.isManualEdit(true);
    };
    LocalAnimeListModel.prototype.showRecommendations = function showRecommendations() {
        var self = this;

        self.isManualEdit(false);
    };
    LocalAnimeListModel.prototype.addManualRating = function addManualRating(item, rating) {
        var self = this;

        self.addItem(item, rating);
    };
    LocalAnimeListModel.prototype.removeManualRating = function removeManualRating(item, rating) {
        var self = this;

        self.removeItem(item);
    };
    LocalAnimeListModel.prototype.trackPage = function trackPage() {
        var self = this;
        var newPage = null;

        var animeList = self.manualAnimeList();

        if (!self.hasSource()) {
            newPage = '/initial_page';
        } else if (self.isManualEdit()) {
            newPage = '/user_list';
        } else if (self.isMalList()) {
            newPage = '/mal_user';
        } else if (self.isManualList()) {
            newPage = '/anonymous_user';
        }

        if (newPage !== self.currentGAPage) {
            ga('set', 'page', newPage);
            ga('send', 'pageview');
            self.currentGAPage = newPage;
        }
    };
    LocalAnimeListModel.prototype.trackLocalAnime = function trackLocalAnime(item, action) {
        ga('send', 'event', {
            eventCategory: 'local_anime',
            eventAction: action,
            eventLabel: '' + item.animedbId,
        });
    };

    var AnimeRecommendationModel = function AnimeRecommendationModel(localAnimeListModel) {
        var self = this;

        self.localAnimeListModel = localAnimeListModel;
        self.recommendedCompletedAnime = ko.observableArray();
        self.recommendedAiringAnime = ko.observableArray();
        self.showRecommendedAiringAnime = ko.observable(false);
        self.tagVisibility = tagVisibility;
        self.tagDescription = tagDescription;

        self.localAnimeListModel.jsonAnimeList.subscribe(self.reloadRecommendations, self);
    };
    AnimeRecommendationModel.prototype.reloadRecommendations = function reloadRecommendations() {
        var self = this;
        var jsonAnimeList = self.localAnimeListModel.jsonAnimeList();
        var lastRecommendationTime = localStorage.getItem('lastRecommendationTime') || 0;
        var lastRecommendation = localStorage.getItem('lastRecommendation');
        var useRecommendation = function useRecommendation (allData) {
            var completed = $.map(allData.completed, function (item) { return new AnimeRecommendation(item, self.localAnimeListModel.isManualList) });
            var airing = $.map(allData.airing, function (item) { return new AnimeRecommendation(item) });
            self.recommendedCompletedAnime(completed);
            self.recommendedAiringAnime(airing);
            localStorage.setItem('lastRecommendationTime', time());
            localStorage.setItem('lastRecommendation', JSON.stringify(allData));
        };

        if (!jsonAnimeList) {
            self.recommendedCompletedAnime([]);
            self.recommendedAiringAnime([]);
            return;
        }

        if (lastRecommendation && time() - lastRecommendationTime < 3600 * 5) {
            useRecommendation(JSON.parse(lastRecommendation));
            return;
        }

        $.post({
            url: "/recommend",
            data: '{"animeList":' + jsonAnimeList + '}',
            dataType: 'json',
            contentType: 'application/json',
            success: useRecommendation
        });
    };
    AnimeRecommendationModel.prototype.addManualRating = function addManualRating(item, rating) {
        var self = this;

        self.localAnimeListModel.addItem(item, rating);
    };
    AnimeRecommendationModel.prototype.toggleAiring = function toggleAiring() {
        var self = this;

        self.showRecommendedAiringAnime(!self.showRecommendedAiringAnime());
    };

    var RecommendationHeaderModel = function RecommendationHeaderModel(localAnimeListModel, animeRecommendationModel) {
        var self = this;

        self.airingStatusDescription = ko.pureComputed(function () {
            return self.animeRecommendationModel.showRecommendedAiringAnime() ?
                "Hide airing anime" :
                "Show airing anime";
        });
        self.isMalList = ko.pureComputed(function () {
            return localAnimeListModel.isMalList();
        });
        self.isManualEdit = ko.pureComputed(function () {
            return localAnimeListModel.isManualEdit();
        });
        self.hasRecommendations = ko.pureComputed(function () {
            return animeRecommendationModel.recommendedCompletedAnime().length ||
                animeRecommendationModel.recommendedAiringAnime().length;
        });
        self.showHideTags = showHideTags;
        self.animeRecommendationModel = animeRecommendationModel;
        self.localAnimeListModel = localAnimeListModel;
    };
    RecommendationHeaderModel.prototype.toggleAiring = function toggleAiring() {
        var self = this;

        self.animeRecommendationModel.toggleAiring();
    };
    RecommendationHeaderModel.prototype.editLocalList = function editLocalList() {
        var self = this;

        self.localAnimeListModel.isManualEdit(true);
    };
    RecommendationHeaderModel.prototype.showRecommendations = function showRecommendations() {
        var self = this;

        self.localAnimeListModel.isManualEdit(false);
    };

    var localAnimeListModel = new LocalAnimeListModel();
    var animeRecommendationModel = new AnimeRecommendationModel(localAnimeListModel);

    ko.applyBindings(animeRecommendationModel, document.getElementById('recommendations'));
    ko.applyBindings(new RecommendationHeaderModel(localAnimeListModel, animeRecommendationModel), document.getElementById('recommendation-filter'));
    ko.applyBindings(localAnimeListModel, document.getElementById('recommendation-source'));
    ko.applyBindings(localAnimeListModel.search, document.getElementById('search-results'));
    ko.applyBindings(localAnimeListModel, document.getElementById('start-page'));
    ko.applyBindings(localAnimeListModel, document.getElementById('main-page-head'));
    ko.applyBindings(localAnimeListModel, document.getElementById('local-list-edit'));

    localAnimeListModel.initialSetup();
});
