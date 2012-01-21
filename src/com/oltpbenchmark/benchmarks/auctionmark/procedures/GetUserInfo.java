/***************************************************************************
 *  Copyright (C) 2012 by H-Store Project                                  *
 *  Brown University                                                       *
 *  Massachusetts Institute of Technology                                  *
 *  Yale University                                                        *
 *                                                                         *
 *  http://hstore.cs.brown.edu/                                            *
 *                                                                         *
 *  Permission is hereby granted, free of charge, to any person obtaining  *
 *  a copy of this software and associated documentation files (the        *
 *  "Software"), to deal in the Software without restriction, including    *
 *  without limitation the rights to use, copy, modify, merge, publish,    *
 *  distribute, sublicense, and/or sell copies of the Software, and to     *
 *  permit persons to whom the Software is furnished to do so, subject to  *
 *  the following conditions:                                              *
 *                                                                         *
 *  The above copyright notice and this permission notice shall be         *
 *  included in all copies or substantial portions of the Software.        *
 *                                                                         *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,        *
 *  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF     *
 *  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. *
 *  IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR      *
 *  OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,  *
 *  ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR  *
 *  OTHER DEALINGS IN THE SOFTWARE.                                        *
 ***************************************************************************/
package com.oltpbenchmark.benchmarks.auctionmark.procedures;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.log4j.Logger;

import com.oltpbenchmark.api.Procedure;
import com.oltpbenchmark.api.SQLStmt;
import com.oltpbenchmark.benchmarks.auctionmark.AuctionMarkConstants;
import com.oltpbenchmark.benchmarks.auctionmark.AuctionMarkConstants.ItemStatus;

/**
 * GetUserInfo
 * 
 * @author pavlo
 * @author visawee
 */
public class GetUserInfo extends Procedure {
    private static final Logger LOG = Logger.getLogger(GetUserInfo.class);

    // -----------------------------------------------------------------
    // STATIC MEMBERS
    // -----------------------------------------------------------------
    
    private static final String ITEM_COLUMNS = "i_id, " +
                                               "i_u_id, " +
                                               "i_name, " + 
                                               "i_current_price, " +
                                               "i_num_bids, " +
                                               "i_end_date, " +
                                               "i_status, " +
                                               "i_iattr0";
    
    // -----------------------------------------------------------------
    // STATEMENTS
    // -----------------------------------------------------------------
    
    public final SQLStmt getUser = new SQLStmt(
        "SELECT u_id, u_rating, u_created, u_balance, u_sattr0, u_sattr1, u_sattr2, u_sattr3, u_sattr4, r_name " +
          "FROM " + AuctionMarkConstants.TABLENAME_USER + ", " +
                    AuctionMarkConstants.TABLENAME_REGION + " " +
         "WHERE u_id = ? AND u_r_id = r_id"
    );

    public final SQLStmt getUserFeedback = new SQLStmt(
        "SELECT u_id, u_rating, u_sattr0, u_sattr1, uf_rating, uf_date, uf_sattr0 " +
        "  FROM " + AuctionMarkConstants.TABLENAME_USER + ", " +
                    AuctionMarkConstants.TABLENAME_USER_FEEDBACK +
        " WHERE u_id = ? AND uf_u_id = u_id " +
        " ORDER BY uf_date DESC LIMIT 25 "
    );

    public final SQLStmt getItemComments = new SQLStmt(
        "SELECT " + ITEM_COLUMNS + ", " +
        "       ic_id, ic_i_id, ic_u_id, ic_buyer_id, ic_question, ic_created " +
        "  FROM " + AuctionMarkConstants.TABLENAME_ITEM + ", " + 
                    AuctionMarkConstants.TABLENAME_ITEM_COMMENT +
        " WHERE i_u_id = ? AND i_status = " + ItemStatus.OPEN.ordinal() + 
        "   AND i_id = ic_i_id AND i_u_id = ic_u_id AND ic_response = ? " +
        " ORDER BY ic_created DESC LIMIT 25 "
    );
    
    public final SQLStmt getSellerItems = new SQLStmt(
        "SELECT " + ITEM_COLUMNS +
         " FROM " + AuctionMarkConstants.TABLENAME_ITEM + " " +
         "WHERE i_u_id = ? " +
         "ORDER BY i_end_date DESC LIMIT 25 "
    );
    
    public final SQLStmt getBuyerItems = new SQLStmt(
        "SELECT " + ITEM_COLUMNS +
         " FROM " + AuctionMarkConstants.TABLENAME_USER_ITEM + ", " +
                    AuctionMarkConstants.TABLENAME_ITEM +
        " WHERE ui_u_id = ? " +
           "AND ui_i_id = i_id AND ui_i_u_id = i_u_id " +
         "ORDER BY i_end_date DESC LIMIT 25 "
    );
    
    public final SQLStmt getWatchedItems = new SQLStmt(
        "SELECT " + ITEM_COLUMNS + ", uw_u_id, uw_created " +
          "FROM " + AuctionMarkConstants.TABLENAME_USER_WATCH + ", " +
                    AuctionMarkConstants.TABLENAME_ITEM +
        " WHERE uw_u_id = ? " +
        "   AND uw_i_id = i_id AND uw_i_u_id = i_u_id " +
        " ORDER BY i_end_date DESC LIMIT 25"
    );

    // -----------------------------------------------------------------
    // RUN METHOD
    // -----------------------------------------------------------------
    
    public ResultSet[] run(Connection conn, Date benchmarkTimes[], long user_id, boolean get_feedback, boolean get_comments, boolean get_seller_items, boolean get_buyer_items, boolean get_watched_items) throws SQLException {
        final boolean debug = LOG.isDebugEnabled();
        
        // The first VoltTable in the output will always be the user's information
        if (debug) LOG.debug("Grabbing USER record: " + user_id);
        PreparedStatement stmt = this.getPreparedStatement(conn, getUser, user_id);
        ResultSet user_result = stmt.executeQuery();

        // They can also get their USER_FEEDBACK records if they want as well
        ResultSet feedback_result = null;
        if (get_feedback) {
            if (debug) LOG.debug("Grabbing USER_FEEDBACK records: " + user_id);
            stmt = this.getPreparedStatement(conn, getUserFeedback, user_id);
            feedback_result = stmt.executeQuery(); 
        }
        
        // And any pending ITEM_COMMENTS that need a response
        ResultSet comments_result = null;
        if (get_comments) {
            if (debug) LOG.debug("Grabbing ITEM_COMMENT records: " + user_id);
            stmt = this.getPreparedStatement(conn, getItemComments, user_id, null);
            comments_result = stmt.executeQuery();
        }
        
        // The seller's items
        ResultSet seller_result = null;
        if (get_seller_items) {
            if (debug) LOG.debug("Grabbing seller's ITEM records: " + user_id);
            stmt = this.getPreparedStatement(conn, getSellerItems, user_id);
            seller_result = stmt.executeQuery();
        }

        // The buyer's purchased items
        ResultSet buyer_items = null;
        if (get_buyer_items) {
            // 2010-11-15: The distributed query planner chokes on this one and makes a plan
            // that basically sends the entire user table to all nodes. So for now we'll just execute
            // the query to grab the buyer's feedback information
            // this.getPreparedStatement(conn, select_seller_feedback, u_id);
            if (debug) LOG.debug("Grabbing buyer's USER_ITEM records: " + user_id);
            stmt = this.getPreparedStatement(conn, getBuyerItems, user_id);
            buyer_items = stmt.executeQuery();
        }
        
        // The buyer's watched items
        ResultSet watched_result = null;
        if (get_watched_items) {
            if (debug) LOG.debug("Grabbing buyer's USER_WATCH records: " + user_id);
            stmt = this.getPreparedStatement(conn, getWatchedItems, user_id);
            watched_result = stmt.executeQuery();
        }

        return new ResultSet[] {
                user_result,
                feedback_result,
                comments_result,
                seller_result,
                buyer_items,
                watched_result
        };
    }
}