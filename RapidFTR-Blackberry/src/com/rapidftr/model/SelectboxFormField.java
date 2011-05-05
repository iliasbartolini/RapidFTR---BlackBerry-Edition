package com.rapidftr.model;

import net.rim.device.api.ui.Field;
import net.rim.device.api.ui.component.ObjectChoiceField;

import com.rapidftr.form.FormField;
import com.rapidftr.form.OptionAction;

public class SelectboxFormField extends CustomField {

	private ObjectChoiceField choiceField;
	private FormField field;

	public SelectboxFormField(FormField field) {
		super(Field.FIELD_LEFT);
		initializeChoiceField(field);
		add(choiceField);
	}

	private void initializeChoiceField(FormField field) {
		this.field = field;
		String[] optionArray = field.getOptionsArray();
		if (optionArray[0] == "") {
			optionArray[0] = "...";
		}
		choiceField = createChoiceField(field.getDisplayName(), optionArray);
	}

	private ObjectChoiceField createChoiceField(String label,
			String[] optionArray) {
		return new ObjectChoiceField(label + ":", optionArray);
	}
	
	public void setValue(final String value) {
		field.forEachOption(new OptionAction() {
			int i = 0;
			public void execute(String option) {
				if(option.equals(value)){
					choiceField.setSelectedIndex(i);
				}
				i++;
			}
		});
	}
}
