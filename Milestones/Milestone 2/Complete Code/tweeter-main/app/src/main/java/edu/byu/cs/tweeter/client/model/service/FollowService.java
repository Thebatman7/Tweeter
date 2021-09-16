package edu.byu.cs.tweeter.client.model.service;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;

import edu.byu.cs.tweeter.model.domain.AuthToken;
import edu.byu.cs.tweeter.model.domain.User;
import edu.byu.cs.tweeter.util.FakeData;
import edu.byu.cs.tweeter.util.Pair;

public class FollowService {


    public interface Observer {
        void handleSuccess(List<User> followees, boolean hasMorePages);
        void handleFailure(String message);
    }

    private Observer observer;
    //
    private GetFollowingHandler getFollowingHandler;
    private GetFollowingTask getFollowingTask;


    //
    public void loadMoreItems(User user, int pageSize, User lastFollowee, Observer observer) {
        getFollowingHandler = new GetFollowingHandler(observer);
        getFollowingTask = new GetFollowingTask(user, pageSize, lastFollowee, observer);
        getFollowingTask.run();
    }

    //constructor
    public FollowService() {}



    /**
     * Background task that retrieves a page of other users being followed by a specified user.
     */
    public static class GetFollowingTask implements Runnable {
        private static final String LOG_TAG = "GetFollowingTask";

        public static final String SUCCESS_KEY = "success";
        public static final String FOLLOWEES_KEY = "followees";
        public static final String MORE_PAGES_KEY = "more-pages";
        public static final String MESSAGE_KEY = "message";
        public static final String EXCEPTION_KEY = "exception";

        /**
         * Auth token for logged-in user.
         */
        private AuthToken authToken;
        /**
         * The user whose following is being retrieved.
         * (This can be any user, not just the currently logged-in user.)
         */
        private User targetUser;
        /**
         * Maximum number of followed users to return (i.e., page size).
         */
        private int limit;
        /**
         * The last person being followed returned in the previous page of results (can be null).
         * This allows the new page to begin where the previous page ended.
         */
        private User lastFollowee;
        /**
         * Message handler that will receive task results.
         */
        private Handler messageHandler;

        //
        private Observer observer;

        public GetFollowingTask(AuthToken authToken, User targetUser, int limit, User lastFollowee,
                                Handler messageHandler) {

            this.authToken = authToken;
            this.targetUser = targetUser;
            this.limit = limit;
            this.lastFollowee = lastFollowee;
            this.messageHandler = messageHandler;
        }

        //
        public GetFollowingTask(User user, int limit, User lastFollowee, Observer observer) {
            this.targetUser = targetUser;
            this.limit = limit;
            this.lastFollowee = lastFollowee;
            this.observer = observer;
        }


        @Override
        public void run() {
            try {
                Pair<List<User>, Boolean> pageOfUsers = getFollowees();

                List<User> followees = pageOfUsers.getFirst();
                boolean hasMorePages = pageOfUsers.getSecond();

                loadImages(followees);

                sendSuccessMessage(followees, hasMorePages);

            } catch (Exception ex) {
                Log.e(LOG_TAG, "Failed to get followees", ex);
                sendExceptionMessage(ex);
            }
        }


        private FakeData getFakeData() {
            return new FakeData();
        }

        private Pair<List<User>, Boolean> getFollowees() {
            return getFakeData().getPageOfUsers((User) lastFollowee, limit, targetUser);
        }

        private void loadImages(List<User> followees) throws IOException {
            for (User u : followees) {
                BackgroundTaskUtils.loadImage(u);
            }
        }

        private void sendSuccessMessage(List<User> followees, boolean hasMorePages) {
            Bundle msgBundle = new Bundle();
            msgBundle.putBoolean(SUCCESS_KEY, true);
            msgBundle.putSerializable(FOLLOWEES_KEY, (Serializable) followees);
            msgBundle.putBoolean(MORE_PAGES_KEY, hasMorePages);

            Message msg = Message.obtain();
            msg.setData(msgBundle);

            //
            observer.handleSuccess(followees, hasMorePages);
            messageHandler.sendMessage(msg);
        }

        private void sendFailedMessage(String message) {
            Bundle msgBundle = new Bundle();
            msgBundle.putBoolean(SUCCESS_KEY, false);
            msgBundle.putString(MESSAGE_KEY, message);

            Message msg = Message.obtain();
            msg.setData(msgBundle);

            //
            observer.handleFailure(msg.toString());
            //messageHandler.sendMessage(msg);
        }

        private void sendExceptionMessage(Exception exception) {
            Bundle msgBundle = new Bundle();
            msgBundle.putBoolean(SUCCESS_KEY, false);
            msgBundle.putSerializable(EXCEPTION_KEY, exception);

            Message msg = Message.obtain();
            msg.setData(msgBundle);

            observer.handleFailure(msg.toString());
            //messageHandler.sendMessage(msg);
        }

    }



    /**
     * Message handler (i.e., observer) for GetFollowingTask.
     */
    private class GetFollowingHandler extends Handler {

        private Observer observer;

        public GetFollowingHandler(Observer observer) {
            this.observer = observer;
        }

        @Override
        public void handleMessage(@NonNull Message msg) {


            boolean success = msg.getData().getBoolean(FollowService.GetFollowingTask.SUCCESS_KEY);
            if (success) {
                List<User> followees = (List<User>) msg.getData().getSerializable(FollowService.GetFollowingTask.FOLLOWEES_KEY);
                boolean hasMorePages = msg.getData().getBoolean(FollowService.GetFollowingTask.MORE_PAGES_KEY);

                observer.handleSuccess(followees, hasMorePages);
                //lastFollowee = (followees.size() > 0) ? followees.get(followees.size() - 1) : null;

                //followingRecyclerViewAdapter.addItems(followees);
            } else if (msg.getData().containsKey(FollowService.GetFollowingTask.MESSAGE_KEY)) {
                String message = msg.getData().getString(FollowService.GetFollowingTask.MESSAGE_KEY);
                observer.handleFailure(message);
                //Toast.makeText(getContext(), "Failed to get following: " + message, Toast.LENGTH_LONG).show();
            } else if (msg.getData().containsKey(FollowService.GetFollowingTask.EXCEPTION_KEY)) {
                Exception ex = (Exception) msg.getData().getSerializable(FollowService.GetFollowingTask.EXCEPTION_KEY);
                String message = "Failed to get following because of exception: " + ex.getMessage();
                observer.handleFailure(message);
                //Toast.makeText(getContext(), "Failed to get following because of exception: " + ex.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }
}
