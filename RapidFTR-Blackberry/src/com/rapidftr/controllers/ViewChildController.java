package com.rapidftr.controllers;

import com.rapidftr.controllers.internal.Controller;
import com.rapidftr.controllers.internal.Dispatcher;
import com.rapidftr.model.Child;
import com.rapidftr.screens.ViewChildScreen;
import com.rapidftr.screens.internal.UiStack;

public class ViewChildController extends Controller {

    private final ViewChildScreen viewChildScreen;

    public ViewChildController(ViewChildScreen viewChildScreen, UiStack uiStack,
                               Dispatcher dispatcher) {
        super(viewChildScreen, uiStack, dispatcher);
        this.viewChildScreen = viewChildScreen;
    }

    public void syncChild(Child child) {
        dispatcher.syncChild(child);
    }

    public void viewChild(Child child) {
        viewChildScreen.setChild(child);
        show();
    }

    public void editChild(Child child) {
        dispatcher.editChild(child);

    }

    public void viewChildPhoto(Child child) {
        dispatcher.viewChildPhoto(child);
    }

    public void showHistory(Child child) {
        dispatcher.showHistory(child);
    }

    public void popScreen() {
        dispatcher.viewChildren();
    }
}
