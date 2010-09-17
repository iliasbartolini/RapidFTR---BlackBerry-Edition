package com.rapidftr.services;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;

import org.json.me.JSONArray;
import org.json.me.JSONException;
import org.json.me.JSONObject;

import com.rapidftr.datastore.ChildrenRecordStore;
import com.rapidftr.model.Child;
import com.rapidftr.net.HttpServer;
import com.rapidftr.net.HttpService;
import com.rapidftr.utilities.HttpUtility;
import com.sun.me.web.path.Result;
import com.sun.me.web.path.ResultException;
import com.sun.me.web.request.Arg;
import com.sun.me.web.request.PostData;
import com.sun.me.web.request.Response;

public class ChildSyncService extends RequestAwareService {

	private static final String FILE_STORE_HOME_USER = "file:///store/home/user";

	private static final String PROCESS_STATE = "process_state";

	private final ChildrenRecordStore childRecordStore;

	public ChildSyncService(HttpService httpService,
			ChildrenRecordStore childRecordStore) {
		super(httpService);
		this.childRecordStore = childRecordStore;
	}

	public void uploadChildRecords() {
		uploadChildren(childRecordStore.getAllChildren());
	}

	private void uploadChildren(Vector childrenList) {
		requestHandler.setRequestInProgress();
		Enumeration children = childrenList.elements();
		int index = 0;
		while (children.hasMoreElements()) {
			Hashtable context = new Hashtable();
			context.put(PROCESS_STATE, "Uploading [" + (++index) + "/"
					+ childrenList.size() + "]");
			Child child = (Child) children.nextElement();
			PostData postData = child.getPostData();
			Arg multiPart = new Arg("Content-Type",
					"multipart/form-data;boundary=" + postData.getBoundary());
			Arg json = HttpUtility.HEADER_ACCEPT_JSON;
			Arg[] httpArgs = { multiPart, json };
			if (child.isNewChild()) {
				requestHandler.incrementActiveRequests(1);
				httpService.post("children", null, httpArgs, requestHandler,
						postData, context);
			} else if (child.isUpdated()) {
				requestHandler.incrementActiveRequests(1);
				httpService.put("children/" + child.getField("_id"), null,
						httpArgs, requestHandler, postData, context);
			}
		}
	}

	public void uploadChildRecord(Child child) {
		Vector childrenList = new Vector();
		childrenList.addElement(child);
		uploadChildren(childrenList);
	}

	public void onRequestSuccess(Object context, Response result) {
		requestHandler.getRequestCallBack().updateProgressMessage(
				((Hashtable) context).get(PROCESS_STATE).toString());
		// sync child with local store
		Child child = new Child();
		try {
			JSONObject jsonChild = new JSONObject(result.getResult().toString());
			HttpServer.printResponse(result);
			JSONArray fieldNames = jsonChild.names();
			for (int j = 0; j < fieldNames.length(); j++) {
				String fieldName = fieldNames.get(j).toString();
				String fieldValue = jsonChild.getString(fieldName);
				child.setField(fieldName, fieldValue);
			}

			try {
				Arg[] httpArgs = new Arg[1];
				httpArgs[0] = HttpUtility.HEADER_CONTENT_TYPE_IMAGE;
				Response response = httpService.get("children/"
						+ child.getField("_id") + "/thumbnail", null, httpArgs);
				byte[] data = response.getResult().getData();

				String storePath = "";
				try {
					String sdCardPath = "file:///SDCard/Blackberry";
					FileConnection fc = (FileConnection) Connector
							.open(sdCardPath);
					if (fc.exists())
						storePath = sdCardPath;
					else
						storePath = FILE_STORE_HOME_USER;
				} catch (IOException ex) {
					storePath = FILE_STORE_HOME_USER;
				}

				String imagePath = storePath + "/pictures/"
						+ (String) child.getField("current_photo_key") + ".jpg";
				FileConnection fc = (FileConnection) Connector.open(imagePath);
				if (!fc.exists()) {
					fc.create(); // create the file if it doesn't exist
				}
				fc.setWritable(true);
				OutputStream outStream = fc.openOutputStream();
				outStream.write(data);
				outStream.close();
				fc.close();

				child.setField("current_photo_key", imagePath);
			} catch (IOException e) {
				e.printStackTrace();
			}

			child.clearEditHistory();
			childRecordStore.addOrUpdateChild(child);
		} catch (JSONException e) {
			// SumitG TODO to handle this by maintaining error queue
			e.printStackTrace();
		}

	}

	public void syncAllChildRecords() throws ServiceException {
		new Thread() {
			public void run() {
				requestHandler.setRequestInProgress();
				requestHandler.getRequestCallBack().updateProgressMessage(
						"Syncing");
				uploadChildRecords();

				try {
					downloadNewChildRecords();
				} catch (IOException e) {
					requestHandler.markProcessFailed();
				} catch (JSONException e) {
					requestHandler.markProcessFailed();
				} catch (Exception e) {
					requestHandler.markProcessComplete();
				}
				if (requestHandler.isProcessCompleted()) {
					requestHandler.markProcessComplete();
				}
			};
		}.start();

	}

	private void downloadNewChildRecords() throws IOException, JSONException {
		Vector childNeedToDownload = childRecordsNeedToBeDownload();
		Enumeration items = childNeedToDownload.elements();
		Arg[] httpArgs = new Arg[1];
		httpArgs[0] = HttpUtility.HEADER_ACCEPT_JSON;
		int index = 0;
		while (items.hasMoreElements()) {
			index++;
			Hashtable context = new Hashtable();
			context.put(PROCESS_STATE, "Downloading [" + index + "/"
					+ childNeedToDownload.size() + "]");
			requestHandler.incrementActiveRequests(1);
			httpService.get("children/" + items.nextElement().toString(), null,
					httpArgs, requestHandler, context);
		}

	}

	private Vector childRecordsNeedToBeDownload() throws IOException,
			JSONException {
		Vector childNeedToDownload = new Vector();
		Hashtable offlineIdRevXREF = getOfflineStoredChildrenIdRevMapping();
		Hashtable onlineIdRevXREF = getOnlineStoredChildrenIdRevMapping();

		Enumeration items = onlineIdRevXREF.keys();

		while (items.hasMoreElements()) {
			String key = (String) items.nextElement();
			if ((!offlineIdRevXREF.containsKey(key))
					|| (offlineIdRevXREF.containsKey(key) && !offlineIdRevXREF
							.get(key).equals(onlineIdRevXREF.get(key)))) {
				childNeedToDownload.addElement(key);
			}
		}
		return childNeedToDownload;
	}

	public Vector getAllChildrenFromOnlineStore() throws IOException,
			JSONException {

		Arg[] httpArgs = new Arg[1];
		httpArgs[0] = HttpUtility.HEADER_ACCEPT_JSON;
		Response response = httpService.get("children", null, httpArgs);
		Result result = response.getResult();
		HttpServer.printResponse(response);
		JSONArray jsonChildren = result.getAsArray("");
		Vector children = new Vector();
		for (int i = 0; i < jsonChildren.length(); i++) {
			JSONObject jsonChild = (JSONObject) jsonChildren.get(i);
			Child child = new Child();
			JSONArray fieldNames = jsonChild.names();
			for (int j = 0; j < fieldNames.length(); j++) {
				String fieldName = fieldNames.get(j).toString();
				String fieldValue = jsonChild.getString(fieldName);
				child.setField(fieldName, fieldValue);
			}

			children.addElement(child);
		}
		return children;

	}

	private Hashtable getOnlineStoredChildrenIdRevMapping()
			throws ServiceException {

		Hashtable mapping = new Hashtable();

		Arg[] httpArgs = new Arg[1];
		httpArgs[0] = HttpUtility.HEADER_ACCEPT_JSON;
		Response response;
		try {
			response = httpService.get("children-ids", null, httpArgs);
			Result result = response.getResult();
			if (requestHandler.isValidResponse(response)) {
				HttpServer.printResponse(response);
				JSONArray jsonChildren = result.getAsArray("");
				for (int i = 0; i < jsonChildren.length(); i++) {
					JSONObject jsonChild = (JSONObject) jsonChildren.get(i);

					mapping.put(jsonChild.getString("id"), jsonChild
							.getString("rev"));
				}
			} else {
				requestHandler.handleResponseErrors(response);
			}
		} catch (JSONException e) {
			throw new ServiceException("JSON Data is invalid Problem");
		} catch (ResultException e) {
			throw new ServiceException("JSON Data is invalid Problem");
		} catch (IOException e) {
			throw new ServiceException("Connection Problem");
		}
		return mapping;
	}

	private Hashtable getOfflineStoredChildrenIdRevMapping() {
		Hashtable mapping = new Hashtable();
		Enumeration items = childRecordStore.getAllChildren().elements();

		while (items.hasMoreElements()) {
			Child child = (Child) items.nextElement();
			Object id = child.getField("_id");
			Object rev = child.getField("_rev");
			if (id != null && rev != null) {
				mapping.put(id.toString(), rev.toString());
			}
		}
		return mapping;
	}

	public Child getChildFromOnlineStore(String id) throws IOException {
		Child child = new Child();
		Arg[] httpArgs = new Arg[1];
		httpArgs[0] = HttpUtility.HEADER_ACCEPT_JSON;
		Response response = httpService.get("children/" + id, null, httpArgs);
		Result result = response.getResult();
		HttpServer.printResponse(response);
		try {
			JSONObject jsonChild = new JSONObject(result.toString());
			JSONArray fieldNames = jsonChild.names();
			for (int j = 0; j < fieldNames.length(); j++) {
				String fieldName = fieldNames.get(j).toString();
				String fieldValue = jsonChild.getString(fieldName);
				child.setField(fieldName, fieldValue);
			}
		} catch (JSONException e) {
			throw new ServiceException(
					"JSON returned from get children is in unexpected format");
		}
		return child;
	}

	public void clearState() {
		childRecordStore.deleteAllChildren();

	}

}