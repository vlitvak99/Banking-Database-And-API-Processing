
/**
*
* @author Vlad Litvak
* @since 05.07.2020
* @version 4.3.5
*
* Before running, download the MySQL JDBC Connector .jar from https://dev.mysql.com/downloads/connector/j/
* and the JSON .jar from http://www.java2s.com/Code/Jar/j/Downloadjsonsimple111jar.htm
* 
* Make sure those files are in the class path, then run this file
* Ex:
* java -cp mysql-connector-java-8.0.19.jar:json-simple-1.1.1.jar APIProcessing.java
*
*/

import java.util.*;
import java.sql.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.json.simple.JSONObject;
import org.json.simple.parser.*;

public class APIProcessing {
    static final String DB_ADDRESS = ""; //redacted
    static final String DB_USERNAME = ""; //redacted
    static final String DB_PASSWORD = ""; //redacted
    static final String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";

    private static Connection con = null;
    private static Statement stmt = null;
    private static ResultSet rs = null;

    /**
    * Stores API ID and Input JSON of an API Request.
    */
    public static class APIRequest{
        private int apiID;
        private int apiKey;
        private String json;

        public APIRequest(int apiID, int apiKey, String json){
            this.apiID = apiID;
            this.apiKey = apiKey;
            this.json = json;
        }
    }

    /** 
    * Intializes a connection to the bank's database.
    *
    * @throws ClassNotFoundException If the JDBC driver is not properly set up.
    */
    public APIProcessing() throws ClassNotFoundException {
        try{
            Class.forName(JDBC_DRIVER);
            con = DriverManager.getConnection(DB_ADDRESS, DB_USERNAME, DB_PASSWORD);
            stmt = con.createStatement();
            System.out.println("Sucess: database connection established");
        }
        catch (SQLException e) {
            System.out.println("Error: database connection failed");
            e.printStackTrace();
        }
    }

    /** 
    * Attempts to register a new user.
    *
    * @param  firstName The new user's first name (must be between 1 and 30 characters, cannot contain apostrophes, quotes, backslashes, or semicolons).
    * @param  lastName  The new user's last name (must be between 1 and 30 characters: 30 characters, cannot contain apostrophes, quotes, backslashes, or semicolons).
    * @param  password  The new user's password (must be between 1 and 30 characters, cannot contain apostrophes, quotes, backslashes, or semicolons).
    * @param  address   The new user's address (must be between 1 and 100 characters, cannot contain apostrophes, quotes, backslashes, or semicolons).
    * @return           A JSON formatted string indicating the success of the user registration (includes a new user ID if successful).
    */
    public static String registerUser(String firstName, String lastName, String password, String address){
        try{
            //first name must be between 1 and 30 valid characters
            if(firstName.length() > 30 || !validString(firstName)) return "{\"Error\":true,\"Error Message\":\"Unable to create user: First name must be between 1 and 30 characters and cannot use apostrophes, quotes, backslashes, or semicolons\"}";
            //last name must be between 1 and 30 valid characters
            if(lastName.length() > 30 || !validString(lastName)) return "{\"Error\":true,\"Error Message\":\"Unable to create user: Last name must be between 1 and 30 characters and cannot use apostrophes, quotes, backslashes, or semicolons\"}";
            //password must be between 1 and 30 valid characters
            if(password.length() > 30 || !validString(password)) return String.format("{\"Error\":true,\"Error Message\":\"Unable to create user (%s %s): Password must be between 1 and 30 characters and cannot use apostrophes, quotes, backslashes, or semicolons\"}", firstName, lastName);
            //address must be between 1 and 100 valid characters
            if(address.length() > 100 || !validString(address)) return String.format("{\"Error\":true,\"Error Message\":\"Unable to create user (%s %s): Address must be between 1 and 100 characters and cannot use apostrophes, quotes, backslashes, or semicolons\"}", firstName, lastName);

            //insert the customer into the Customers table
            String insert = String.format("INSERT INTO Customers (FirstName, LastName, Password, Address) VALUES ('%s', '%s', '%s', '%s');", firstName, lastName, password, address);
            stmt.execute(insert);
            //get json with customer info, including Customer ID
            String getID = String.format("SELECT MAX(CustomerID) From Customers WHERE FirstName = '%s' AND LastName = '%s' AND Password = '%s' AND Address = '%s';", firstName, lastName, password, address);
            rs = stmt.executeQuery(getID);
            while(rs.next()) return String.format("{\"Error\":false,\"User ID\":%s,\"First Name\":\"%s\",\"Last Name\":\"%s\"}", rs.getInt(1), firstName, lastName);
            return String.format("{\"Error\":true,\"Error Message\":\"Unable to create user (%s %s)\"}", firstName, lastName);
        }
        catch (SQLException e) {
            e.printStackTrace();
            //returns json indicating an error
            return String.format("{\"Error\":true,\"Error Message\":\"Unable to create user (%s %s)\"}", firstName, lastName);
        }
    }

    /** 
    * Attempts to send a login request.
    * A successful login request means that the request was processed, to check whether the login was successful, see the “Login Successful” value.
    * An error response should NEVER be treated as a successful login.
    *
    * @param  customerID The customer's user ID.
    * @param  password   The customer's password.
    * @return            A JSON formatted string indicating the success of the login.
    */
    public static String login(int customerID, String password){
        try{
            //if the password is valid and correct, return a successful login json
            if(validString(password) && passwordMatch(customerID, password)) return String.format("{\"Error\":false,\"User ID\":%d,\"Login Successful\":true}", customerID);
            //otherwise return a failed login json
            return String.format("{\"Error\":false,\"User ID\":%d,\"Login Successful\":false}", customerID);
        }
        catch (SQLException e) {
            e.printStackTrace();
            //returns json indicating an error
            return String.format("{\"Error\":true,\"Error Message\":\"Unable to determine login for User #%d\"}", customerID);
        }
    }

    /** 
    * Attempts to change a user's name.
    *
    * @param  customerID   The customer's user ID (must be valid).
    * @param  password     The customer's password (must match user ID).
    * @param  newFirstName The customer's new first name (must be between 1 and 30 characters, cannot contain apostrophes, quotes, backslashes, or semicolons).
    * @param  newLastName  The customer's new last name (must be between 1 and 30 characters, cannot contain apostrophes, quotes, backslashes, or semicolons).
    * @return              A JSON formatted string indicating the success of the name change.
    */
    public static String changeName(int customerID, String password, String newFirstName, String newLastName){
        try{
            //user ID and password must match
            if(!validString(password) || !passwordMatch(customerID, password)) return String.format("{\"Error\":true,\"Error Message\":\"Unable to change name for User #%d: User ID and password do not match\"}", customerID);
            //first name must be between 1 and 30 valid characters
            if(newFirstName.length() > 30 || !validString(newFirstName)) return String.format("{\"Error\":true,\"Error Message\":\"Unable to change name for User #%d: First name must be between 1 and 30 characters and cannot use apostrophes, quotes, backslashes, or semicolons\"}", customerID);
            //last name must be between 1 and 30 valid characters
            if(newLastName.length() > 30 || !validString(newLastName)) return String.format("{\"Error\":true,\"Error Message\":\"Unable to change name for User #%d: Last name must be between 1 and 30 characters and cannot use apostrophes, quotes, backslashes, or semicolons\"}", customerID);

            //update the name
            String updateName = String.format("UPDATE Customers SET FirstName = '%s', LastName = '%s' WHERE CustomerID = %d;", newFirstName, newLastName, customerID);
            stmt.executeUpdate(updateName);
            String verifyUpdate = String.format("SELECT FirstName, LastName FROM Customers WHERE CustomerID = %d;", customerID);
            rs = stmt.executeQuery(verifyUpdate);
            while(rs.next()) return String.format("{\"Error\":false,\"User ID\":%d,\"New First Name\":\"%s\",\"New Last Name\":\"%s\"}", customerID, rs.getString(1), rs.getString(2)); 
            return String.format("{\"Error\":true,\"Error Message\":\"Unable to change name for User #%d\"}", customerID);
        }
        catch (SQLException e) {
            e.printStackTrace();
            //returns json indicating an error
            return String.format("{\"Error\":true,\"Error Message\":\"Unable to change name for User #%d\"}", customerID);
        }
    }

    /** 
    * Attempts to change a user's address.
    *
    * @param  customerID The customer's user ID (must be valid).
    * @param  password   The customer's password (must match user ID).
    * @param  newAddress The customer's new address (must be between 1 and 100 characters, cannot contain apostrophes, quotes, backslashes, or semicolons).
    * @return            A JSON formatted string indicating the success of the address change.
    */
    public static String changeAddress(int customerID, String password, String newAddress){
        try{
            //user ID and password must match
            if(!validString(password) || !passwordMatch(customerID, password)) return String.format("{\"Error\":true,\"Error Message\":\"Unable to change address for User #%d: User ID and password do not match\"}", customerID);
            //address must be between 1 and 100 valid characters
            if(newAddress.length() > 100 || !validString(newAddress)) return String.format("{\"Error\":true,\"Error Message\":\"Unable to change address for User #%d: Address must be between 1 and 100 characters and cannot use apostrophes, quotes, backslashes, or semicolons\"}", customerID);

            //update the address
            String updateAddress = String.format("UPDATE Customers SET Address = '%s' WHERE CustomerID = %d;", newAddress, customerID);
            stmt.executeUpdate(updateAddress);
            String verifyUpdate = String.format("SELECT Address FROM Customers WHERE CustomerID = %d;", customerID);
            rs = stmt.executeQuery(verifyUpdate);
            while(rs.next()) return String.format("{\"Error\":false,\"User ID\":%d,\"New Address\":\"%s\"}", customerID, rs.getString(1)); 
            return String.format("{\"Error\":true,\"Error Message\":\"Unable to change address for User #%d\"}", customerID);
        }
        catch (SQLException e) {
            e.printStackTrace();
            //returns json indicating an error
            return String.format("{\"Error\":true,\"Error Message\":\"Unable to change address for User #%d\"}", customerID);
        }
    }

    /** 
    * Attempts to change a user's password.
    *
    * @param  customerID  The customer's user ID (must be valid).
    * @param  oldPassword The customer's old password (must match user ID).
    * @param  newPassword The customer's new password (must be between 1 and 30 characters, cannot contain apostrophes, quotes, backslashes, or semicolons).
    * @return             A JSON formatted string indicating the success of the password change.
    */
    public static String changePassword(int customerID, String oldPassword, String newPassword){
        try{
            //user ID and current password must match
            if(!validString(oldPassword) || !passwordMatch(customerID, oldPassword)) return String.format("{\"Error\":true,\"Error Message\":\"Unable to change password for User #%d: Current password is incorrect\"}", customerID);
            //password must be between 1 and 30 valid characters
            if(newPassword.length() > 30 || !validString(newPassword)) return String.format("{\"Error\":true,\"Error Message\":\"Unable to change password for User #%d: Password must be between 1 and 100 characters and cannot use apostrophes, quotes, backslashes, or semicolons\"}", customerID);
            //new password must be different
            if(oldPassword.equals(newPassword)) return String.format("{\"Error\":true,\"Error Message\":\"Unable to change password for User #%d: New password must be different than the current password\"}", customerID);

            //update the password
            String updatePassword = String.format("UPDATE Customers SET Password = '%s' WHERE CustomerID = %d;", newPassword, customerID);
            stmt.executeUpdate(updatePassword);
            String verifyUpdate = String.format("SELECT Password FROM Customers WHERE CustomerID = %d;", customerID);
            rs = stmt.executeQuery(verifyUpdate);
            while(rs.next()) if(newPassword.equals(rs.getString(1))) return String.format("{\"Error\":false,\"User ID\":%d,\"Password Changed\":true}", customerID);
            return String.format("{\"Error\":true,\"Error Message\":\"Unable to change password for User #%d\"}", customerID);
        }
        catch (SQLException e) {
            e.printStackTrace();
            //returns json indicating an error
            return String.format("{\"Error\":true,\"Error Message\":\"Unable to change password for User #%d\"}", customerID);
        }
    }

    /** 
    * Attempts to open a new bank account for a user.
    *
    * @param  customerID     The customer's user ID (must be valid).
    * @param  password       The customer's password (must match user ID).
    * @param  accountName    The new account's name (must be between 1 and 50 characters, cannot contain apostrophes, quotes, backslashes, or semicolons).
    * @param  type           The account type (must be either "C" for Checking or "S" for Savings).
    * @param  initialDeposit The initial deposit for the new account (must be formatted as a number with two digits after the decimal [i.e 100.00], must be greater than or equal to zero and less than one trillion).
    * @return                A JSON formatted string indicating the success of the account creation (includes a new account ID if successful).
    */
    public static String openAccount(int customerID, String password, String accountName, String type, double initialDeposit){
        try{
            //user ID and password must match
            if(!validString(password) || !passwordMatch(customerID, password)) return String.format("{\"Error\":true,\"Error Message\":\"Unable to create account for User #%d: User ID and password do not match\"}", customerID);
            //account name must be between 1 and 50 valid characters
            if(accountName.length() > 50 || !validString(accountName)) return String.format("{\"Error\":true,\"Error Message\":\"Unable to create account for User #%d: Account name must be between 1 and 50 characters and cannot use apostrophes, quotes, backslashes, or semicolons\"}", customerID);
            //the account name cannot be a duplicate of another one of the user's accounts
            if(accountNameExists(customerID, accountName)) return String.format("{\"Error\":true,\"Error Message\":\"Unable to create account (%s) for User #%d: User already has an account with this name\"}", accountName, customerID);
            //the intial deposit cannot be negative
            if(initialDeposit < 0) return String.format("{\"Error\":true,\"Error Message\":\"Unable to create account (%s) for User #%d: Initial deposit cannot be negative\"}", accountName, customerID);
            //the intial deposit cannot be more than $999,999,999,999,999.99
            if(initialDeposit >= 1000000000000.00) return String.format("{\"Error\":true,\"Error Message\":\"Unable to create account (%s) for User #%d: Initial deposit cannot exceed $999,999,999,999.99\"}", accountName, customerID);
            //the type must be either 'C' for Checking or 'S' for Savings
            if((!type.equals("C") && !type.equals("S"))) return String.format("{\"Error\":true,\"Error Message\":\"Unable to create account (%s) for User #%d: Account type may be only 'C' for Checking or 'S' for Savings\"}", accountName, customerID);
            //initial deposit can only have a maximum of two digits after the decimal
            BigDecimal formattedInitialDeposit = new BigDecimal(String.valueOf(initialDeposit)).setScale(2, RoundingMode.FLOOR);
            if(formattedInitialDeposit.doubleValue() != initialDeposit) return String.format("{\"Error\":true,\"Error Message\":\"Unable to create account (%s) for User #%d: Initial deposit cannot have fractions of cents\"}", accountName, customerID);

            //insert the account into the Accounts table
            String insert = String.format("INSERT INTO Accounts (AccountName, Balance, Type, CustomerID) VALUES ('%s', %s, '%s', %d);", accountName, formattedInitialDeposit.toString() type, customerID);
            stmt.execute(insert);
            //gets account ID
            String getID = String.format("SELECT MAX(AccountID) From Accounts WHERE AccountName = '%s' AND CustomerID = %d;", accountName, customerID);
            int accountID = 0;
            rs = stmt.executeQuery(getID);
            while(rs.next()) accountID = rs.getInt(1);
            //if no account ID is found, return a json indicating an error
            if(accountID == 0) return String.format("{\"Error\":true,\"Error Message\":\"Unable to create account (%s) for User #%d\"}", accountName, customerID);

            //return json with account info, including Account ID
            String expandedType = "";
            if(type.equals("C")) expandedType = "Checking";
            else expandedType = "Savings";
            String verifyBalance = String.format("SELECT Balance FROM Accounts WHERE AccountID = %d;", accountID);
            rs = stmt.executeQuery(verifyBalance);
            while(rs.next()) return String.format("{\"Error\":false,\"User ID\":%d,\"Account\":{\"Account ID\":%d,\"Account Name\":\"%s\",\"Account Type\":\"%s\",\"Account Status\":\"Active\"\"Balance\":\"$%s\"}}", customerID, accountID, accountName, expandedType, rs.getString(1));
            return String.format("{\"Error\":true,\"Error Message\":\"Unable to create account (%s) for User #%d\"}", accountName, customerID);
        }
        catch (SQLException e) {
            e.printStackTrace();
            //returns json indicating an error
            return String.format("{\"Error\":true,\"Error Message\":\"Unable to create account (%s) for User #%d\"}", accountName, customerID);
        }
    }

    /** 
    * Attempts to change an accounts name.
    *
    * @param  customerID     The customer's user ID (must be valid).
    * @param  password       The customer's password (must match user ID).
    * @param  accountID      The account ID (must be active and owned by the specified user).
    * @param  newAccountName The account's new name (must be between 1 and 50 characters, cannot contain apostrophes, quotes, backslashes, or semicolons).
    * @return                A JSON formatted string indicating the success of the account name change.
    */
    public static String changeAccountName(int customerID, String password, int accountID, String newAccountName){
        try{
            //user ID and password must match
            if(!validString(password) || !passwordMatch(customerID, password)) return String.format("{\"Error\":true,\"Error Message\":\"Unable to change name for Account #%d: User ID and password do not match\"}", accountID);
            //user must own the account
            if(!customersAccountIsActive(customerID, accountID)) return String.format("{\"Error\":true,\"Error Message\":\"Unable to change name for Account #%d (User #%d): User has no active account with this account number\"}", accountID, customerID);
            //new account name must be between 1 and 50 valid characters
            if(newAccountName.length() > 50 || !validString(newAccountName)) return String.format("{\"Error\":true,\"Error Message\":\"Unable to change name for Account #%d: Account name must be between 1 and 50 characters and cannot use apostrophes, quotes, backslashes, or semicolons\"}", accountID);

            //update the account name
            String updateAccountName = String.format("UPDATE Accounts SET AccountName = '%s' WHERE AccountID = %d;", newAccountName, accountID);
            stmt.executeUpdate(updateAccountName);
            String verifyUpdate = String.format("SELECT AccountName FROM Accounts WHERE AccountID = %d;", accountID);
            rs = stmt.executeQuery(verifyUpdate);
            while(rs.next()) return String.format("{\"Error\":false,\"User ID\":%d,\"Account ID\":%d,\"New Account Name\":\"%s\"}", customerID, accountID, rs.getString(1)); 
            return String.format("{\"Error\":true,\"Error Message\":\"Unable to change name for Account #%d\"}", accountID);
        }
        catch (SQLException e) {
            e.printStackTrace();
            //returns json indicating an error
            return String.format("{\"Error\":true,\"Error Message\":\"Unable to change name for Account #%d\"}", accountID);
        }
    }

    /** 
    * Attempts to close a user's account, withdraws remaining account balance.
    *
    * @param  customerID The customer's user ID (must be valid).
    * @param  password   The customer's password (must match user ID).
    * @param  accountID  The account ID (must be active and owned by the specified user).
    * @return            A JSON formatted string indicating the success of the account closing.
    */
    public static String closeAccount(int customerID, String password, int accountID){
        try{
            //customer ID and password must match
            if(!validString(password) || !passwordMatch(customerID, password)) return String.format("{\"Error\":true,\"Error Message\":\"Unable to close Account #%d (User #%d): User ID and password do not match\"}", accountID, customerID);
            //user must own the account
            if(!customersAccountIsActive(customerID, accountID)) return String.format("{\"Error\":true,\"Error Message\":\"Unable to close Account #%d (User #%d): User has no active account with this account number\"}", accountID, customerID);

            //get balance of account
            String getBalance = String.format("SELECT MAX(Balance) FROM Accounts WHERE AccountID = %d;", accountID);
            double balance = 0;
            rs = stmt.executeQuery(getBalance);
            while(rs.next()) balance = rs.getDouble(1);
            //withdraw the full balance of the account
            withdraw(customerID, password, accountID, balance);
            //manually sets balance to 0 in case withdraw fails
            String setInactive = String.format("UPDATE Accounts SET Status = 'I', Balance = 0 WHERE AccountID = %d;", accountID);
            stmt.executeUpdate(setInactive);
            return String.format("{\"Error\":false,\"User ID\":%d,\"Account ID\":%d,\"Closed\":true}", customerID, accountID);

        }
        catch (SQLException e) {
            e.printStackTrace();
            //returns json indicating an error
            return String.format("{\"Error\":true,\"Error Message\":\"Unable to close account (%d) for User #%d\"}", accountID, customerID);
        }
    }

    /** 
    * Attempts to send a withdraw request.
    * A successful withdraw request means that the request was processed, to check whether the transaction went through or not, see the “Status” value.
    *
    * @param  customerID The customer's user ID (must be valid).
    * @param  password   The customer's password (must match user ID).
    * @param  accountID  The account to be withdrawn from (must be active and owned by the specified user).
    * @param  amount     The amount to be withdrawn from the account (must be formatted as a number with two digits after the decimal [i.e 100.00], must be greater than zero and less than one trillion).
    * @return            A JSON formatted string indicating the success of the withdraw request (includes the status of the transaction).
    */
    public static String withdraw(int customerID, String password, int accountID, double amount){
        try{
            //user ID and password must match
            if(!validString(password) || !passwordMatch(customerID, password)) return String.format("{\"Error\":true,\"Error Message\":\"Unable to withdraw $%.2f from Account #%d: User ID and password do not match\"}", amount, accountID);
            //only a positive amount can be withdrawn
            if(amount <= 0) return String.format("{\"Error\":true,\"Error Message\":\"Unable to withdraw $%.2f from Account #%d: Withdraw amount must be positive\"}", amount, accountID);
            //withdraw cannot exceed $999,999,999,999.99
            if(amount >= 1000000000000.00) return String.format("{\"Error\":true,\"Error Message\":\"Unable to withdraw $%.2f from Account #%d: Withdraw amount cannot exceed $999,999,999,999.99\"}", amount, accountID);
            //the user must be the owner of the account
            if(!customersAccountIsActive(customerID, accountID)) return String.format("{\"Error\":true,\"Error Message\":\"Unable to withdraw $%.2f from Account #%d: User #%d does not have an active account with this account #\"}", amount, accountID, customerID);
            
            //amount can only have a maximum of two digits after the decimal
            BigDecimal formattedAmount = new BigDecimal(String.valueOf(amount)).setScale(2, RoundingMode.FLOOR);
            if(formattedAmount.doubleValue() != amount) return String.format("{\"Error\":true,\"Error Message\":\"Unable to withdraw $%.2f from Account #%d: Withdraw amount cannot have fractions of cents\"}", amount, accountID);

            //insert the withdraw into the Transactions table
            String withdrawRequest = String.format("INSERT INTO Transactions (SourceAccount, CustomerID, Amount) VALUES (%d, %d, %s);", accountID, customerID, formattedAmount.toString());
            stmt.execute(withdrawRequest);
            //get the transaction ID
            String getTransactionID = String.format("SELECT MAX(TransactionID) FROM Transactions WHERE SourceAccount = %d AND CustomerID = %d AND Amount = %s;", accountID, customerID, formattedAmount.toString());
            rs = stmt.executeQuery(getTransactionID);
            int transactionID = 0;
            boolean transactionFound = false;
            while(rs.next()){
                transactionID = rs.getInt(1);
                transactionFound = true;
            }
            //if the transaction ID was found, get put the transaction information into a json and return it
            if(transactionFound){
                String getTransferInfo = String.format("SELECT Amount, Status, TimeOfTransaction FROM Transactions WHERE TransactionID = %d;", transactionID);
                rs = stmt.executeQuery(getTransferInfo);
                while(rs.next())
                    return String.format("{\"Error\":false,\"Transaction ID\":%d,\"Transaction Type\":\"Withdraw\",\"Amount\":\"$%s\",\"Account ID\":%d,\"User ID\":%d,\"Status\":\"%s\",\"Time of Transaction\":\"%s\"}", transactionID, rs.getString(1), accountID, customerID, rs.getString(2), rs.getString(3));
            }
            //if the transaction ID could not be retrieved, indicate an error in a json and return it
            return String.format("{\"Error\":true,\"Error Message\":\"Unable to withdraw $%s from Account #%d\"}", formattedAmount.toString(), accountID);
        }
        catch (SQLException e) {
            e.printStackTrace();
            //returns json indicating an error
            return String.format("{\"Error\":true,\"Error Message\":\"Unable to withdraw $%.2f from Account #%d\"}", amount, accountID);
        }
    }

    /** 
    * Attempts to send a deposit request.
    * A successful deposit request means that the request was processed, to check whether the transaction went through or not, see the “Status” value.
    *
    * @param  customerID The customer's user ID (must be valid).
    * @param  password   The customer's password (must match user ID).
    * @param  accountID  The account to be deposited into (must be active and owned by the specified user).
    * @param  amount     The amount to be deposited into the account (must be formatted as a number with two digits after the decimal [i.e 100.00], must be more than zero and less than one trillion).
    * @return            A JSON formatted string indicating the success of the deposit request (includes the status of the transaction if the request is sent).
    */
    public static String deposit(int customerID, String password, int accountID, double amount){
        try{
            //user ID and password must match
            if(!validString(password) || !passwordMatch(customerID, password)) return String.format("{\"Error\":true,\"Error Message\":\"Unable to depost $%.2f into Account #%d: User ID and password do not match\"}", amount, accountID);
            //only a positive amount can be deposited
            if(amount <= 0) return String.format("{\"Error\":true,\"Error Message\":\"Unable to deposit $%.2f into Account #%d: Deposit amount must be positive\"}", amount, accountID);
            //deposit cannot exceed $999,999,999,999.99
            if(amount >= 1000000000000.00) return String.format("{\"Error\":true,\"Error Message\":\"Unable to deposit $%.2f into Account #%d: Deposit amount cannot exceed $999,999,999,999.99\"}", amount, accountID);
            //the account must be owned by the user making the deposit
            if(!customersAccountIsActive(customerID, accountID)) return String.format("{\"Error\":true,\"Error Message\":\"Unable to deposit $%.2f into Account #%d: User #%d has no active accounts with this account number\"}", amount, accountID, customerID);
            
            //amount can only have a maximum of two digits after the decimal
            BigDecimal formattedAmount = new BigDecimal(String.valueOf(amount)).setScale(2, RoundingMode.FLOOR);
            if(formattedAmount.doubleValue() != amount) return String.format("{\"Error\":true,\"Error Message\":\"Unable to deposit $%.2f into Account #%d: Deposit amount cannot have fractions of cents\"}", amount, accountID);

            //insert the deposit into the Transactions table
            String depositRequest = String.format("INSERT INTO Transactions (DestinationAccount, CustomerID, Amount) VALUES (%d, %d, %s);", accountID, customerID, formattedAmount.toString());
            stmt.execute(depositRequest);
            //get the transaction ID
            String getTransactionID = String.format("SELECT MAX(TransactionID) FROM Transactions WHERE DestinationAccount = %d AND CustomerID = %d AND Amount = %s;", accountID, customerID, formattedAmount.toString());
            rs = stmt.executeQuery(getTransactionID);
            int transactionID = 0;
            boolean transactionFound = false;
            while(rs.next()){
                transactionID = rs.getInt(1);
                transactionFound = true;
            }
            //if the transaction ID was found, get put the transaction information into a json and return it
            if(transactionFound){
                String getTransferInfo = String.format("SELECT Amount, Status, TimeOfTransaction FROM Transactions WHERE TransactionID = %d;", transactionID);
                rs = stmt.executeQuery(getTransferInfo);
                while(rs.next()) return String.format("{\"Error\":false,\"Transaction ID\":%d,\"Transaction Type\":\"Deposit\",\"Amount\":\"$%s\",\"Account ID\":%d,\"User ID\":%d,\"Status\":\"%s\",\"Time of Transaction\":\"%s\"}", transactionID, rs.getString(1), accountID, customerID, rs.getString(2), rs.getString(3));
            }
            //if the transaction ID could not be retrieved, indicate an error in a json and return it
            return String.format("{\"Error\":true,\"Error Message\":\"Unable to deposit $%s into Account #%d\"}", formattedAmount.toString(), accountID);
        }
        catch (SQLException e) {
            e.printStackTrace();
            //returns json indicating an error
            return String.format("{\"Error\":true,\"Error Message\":\"Unable to deposit $%.2f into Account #%d\"}", amount, accountID);
        }
    }

    /** 
    * Attempts to send a transfer request.
    * A successful transfer request means that the request was processed, to check whether the transaction went through or not, see the “Status” value.
    *
    * @param  customerID           The customer's user ID (must be valid).
    * @param  password             The customer's password (must match user ID).
    * @param  sourceAccountID      The account to be withdrawn from (must be active and owned by the specified user).
    * @param  destinationAccountID The account to be deposited into (must be activ, must be different than the source account).
    * @param  amount               The amount to be transferred from the source account to the destination account (must be formatted as a number with two digits after the decimal [i.e 100.00], must be more than zero and less than one trillion).
    * @return                      A JSON formatted string indicating the success of the transfer request (includes the status of the transaction if the request is sent).
    */
    public static String transfer(int customerID, String password, int sourceAccountID, int destinationAccountID, double amount){
        try{
            //user ID and password must match
            if(!validString(password) || !passwordMatch(customerID, password)) return String.format("{\"Error\":true,\"Error Message\":\"Unable to transfer $%.2f from Account #%d to Account #%d: User ID and password do not match\"}", amount, sourceAccountID, destinationAccountID);
            //only a positive amount can be transfered
            if(amount <= 0) return String.format("{\"Error\":true,\"Error Message\":\"Unable to transfer $%.2f from Account #%d to Account #%d: Transfer amount must be positive\"}", amount, sourceAccountID, destinationAccountID);
            //transfer cannot exceed $999,999,999,999.99
            if(amount >= 1000000000000.00) return String.format("{\"Error\":true,\"Error Message\":\"Unable to transfer $%.2f from Account #%d to Account #%d: Transfer amount cannot exceed $999,999,999,999.99\"}", amount, sourceAccountID, destinationAccountID);
            //source and destination account must be different
            if(sourceAccountID == destinationAccountID) return String.format("{\"Error\":true,\"Error Message\":\"Unable to transfer $%.2f from Account #%d to Account #%d: Cannot transfer from an account to itself\"}", amount, sourceAccountID, destinationAccountID);
            //the source account must be owned by the user making the transfer
            if(!customersAccountIsActive(customerID, sourceAccountID)) return String.format("{\"Error\":true,\"Error Message\":\"Unable to transfer $%.2f from Account #%d to Account #%d: User #%d has no active accounts with the Account Number %d\"}", amount, sourceAccountID, destinationAccountID, customerID, sourceAccountID);
            //the destination account must exist
            if(!accountIsActive(destinationAccountID)) return String.format("{\"Error\":true,\"Error Message\":\"Unable to transfer $%.2f from Account #%d to Account #%d: Account #%d is not an active account\"}", amount, sourceAccountID, destinationAccountID, destinationAccountID);
            
            //amount can only have a maximum of two digits after the decimal
            BigDecimal formattedAmount = new BigDecimal(String.valueOf(amount)).setScale(2, RoundingMode.FLOOR);
            if(formattedAmount.doubleValue() != amount) return String.format("{\"Error\":true,\"Error Message\":\"Unable to transfer $%.2f from Account #%d to Account #%d: Deposit amount cannot have fractions of cents\"}", amount, sourceAccountID, destinationAccountID);

            //insert the transfer into the Transactions table
            String transferRequest = String.format("INSERT INTO Transactions (SourceAccount, DestinationAccount, CustomerID, Amount) VALUES (%d, %d, %d, %s);", sourceAccountID, destinationAccountID, customerID, formattedAmount.toString());
            stmt.execute(transferRequest);
            //get the transaction ID
            String getTransactionID = String.format("SELECT MAX(TransactionID) FROM Transactions WHERE SourceAccount = %d AND DestinationAccount = %d AND CustomerID = %d AND Amount = %s;", sourceAccountID, destinationAccountID, customerID, formattedAmount.toString());
            rs = stmt.executeQuery(getTransactionID);
            int transactionID = 0;
            boolean transactionFound = false;
            while(rs.next()){
                transactionID = rs.getInt(1);
                transactionFound = true;
            }
            //if the transaction ID was found, get put the transaction information into a json and return it
            if(transactionFound){
                String getTransferInfo = String.format("SELECT Amount, Status, TimeOfTransaction FROM Transactions WHERE TransactionID = %d;", transactionID);
                rs = stmt.executeQuery(getTransferInfo);
                while(rs.next())
                    return String.format("{\"Error\":false,\"Transaction ID\":%d,\"Transaction Type\":\"Transfer\",\"Amount\":\"$%s\",\"Source Account ID\":%d,\"Destination Account ID\":%d,\"User ID\":%d,\"Status\":\"%s\",\"Time of Transaction\":\"%s\"}", transactionID, rs.getString(1), sourceAccountID, destinationAccountID, customerID, rs.getString(2), rs.getString(3));
            }
            //if the transaction ID could not be retrieved, indicate an error in a json and return it
            return String.format("{\"Error\":true,\"Error Message\":\"Unable to transfer $%s from Account #%d to Account #%d\"}", formattedAmount.toString(), sourceAccountID, destinationAccountID);
        }
        catch (SQLException e) {
            e.printStackTrace();
            //returns json indicating an error
            return String.format("{\"Error\":true,\"Error Message\":\"Unable to transfer $%.2f from Account #%d to Account #%d\"}", amount, sourceAccountID, destinationAccountID);
        }
    }

    /** 
    * Attempts to get a user's personal inormation.
    *
    * @param  customerID The customer's user ID (must be valid).
    * @param  password   The customer's password (must match user ID).
    * @return            A JSON formatted string indicating the success of the request (includes the user's information if successful).
    */
    public static String userInfo(int customerID, String password){
        try{
            //user ID and password must match
            if(!validString(password) || !passwordMatch(customerID, password)) return String.format("{\"Error\":true,\"Error Message\":\"Cannot get user info for User #%d: User ID and password do not match\"}", customerID);
            //gets user info
            String infoQuery = String.format("SELECT FirstName, LastName, Address FROM Customers WHERE CustomerID = %d;", customerID);
            rs = stmt.executeQuery(infoQuery);
            //return user info json
            while(rs.next()) return String.format("{\"Error\":false,\"User ID\":%d,\"First Name\":\"%s\",\"Last Name\":\"%s\",\"Address\":\"%s\"}", customerID, rs.getString(1), rs.getString(2), rs.getString(3));
            //if no user was found, return json indicating an error
            return String.format("{\"Error\":true,\"Error Message\":\"Cannot get user info for User #%d\"}", customerID);
        }
        catch (SQLException e) {
            e.printStackTrace();
            //returns json indicating an error
            return String.format("{\"Error\":true,\"Error Message\":\"Cannot get user info for User #%d\"}", customerID);
        }
    }

    /** 
    * Attempts to get a list of a user's accounts.
    *
    * @param  customerID      The customer's user ID (must be valid).
    * @param  password        The customer's password (must match user ID).
    * @param  includeInactive Whether to include inactive accounts in the list (if true, inactive accounts will be included, otherwise they will not).
    * @return                 A JSON formatted string indicating the success of the request (includes account information if successful).
    */
    public static String userAccountSummary(int customerID, String password, boolean includeInactive){
        try{
            //user ID and password must match
            if(!validString(password) || !passwordMatch(customerID, password)) return String.format("{\"Error\":true,\"Error Message\":\"Unable to get summary of User #%d's accounts: User ID and password do not match\"}", customerID);
            //gets account info about each of the user's accounts
            String summaryQuery = "";
            if(includeInactive) summaryQuery = String.format("SELECT AccountID, AccountName, Balance, Type, Status FROM Accounts WHERE CustomerID = %d;", customerID);
            else summaryQuery = String.format("SELECT AccountID, AccountName, Balance, Type, Status FROM Accounts WHERE CustomerID = %d AND Status = 'A';", customerID);
            rs = stmt.executeQuery(summaryQuery);
            ArrayList<String> accountJSONs = new ArrayList<>();
            //creates a json object for each account
            while(rs.next()){
                String type = "";
                String status = "";
                if(rs.getString(4).equals("C")) type = "Checking";
                else type = "Savings";
                if(rs.getString(5).equals("A")) status = "Active";
                else status = "Inactive";
                accountJSONs.add(String.format("{\"Account ID\":%s,\"Account Name\":\"%s\",\"Account Type\":\"%s\",\"Account Status\":\"%s\",\"Balance\":\"$%s\"}", rs.getInt(1), rs.getString(2), type, status, rs.getString(3)));
            }
            //creates output json, putting account jsons in a json array
            String output = String.format("{\"Error\":false,\"User ID\":%d,\"Accounts\":[", customerID);
            for(String accountJSON : accountJSONs)
                output += accountJSON + ",";
            if(accountJSONs.size() > 0) return output.substring(0, output.length() - 1) + "]}";
            return output + "]}";
        }
        catch (SQLException e) {
            e.printStackTrace();
            //returns json indicating an error
            return String.format("{\"Error\":true,\"Error Message\":\"Unable to get summary of User #%d's accounts\"}", customerID);
        }
    }

    /** 
    * Attempts to get an user's transaction history in order of most recent transaction first.
    *
    * @param  customerID The customer's user ID (must be valid).
    * @param  password   The customer's password (must match user ID).
    * @param  limit      The maximum number of transactions to be shown (if limit is 0 or less, no limit will be applied).
    * @return            A JSON formatted string indicating the success of the request (includes the transaction history if successful).
    */
    public static String userTransactionHistory(int customerID, String password, int limit){
        try{
            //user ID and password must match
            if(!validString(password) || !passwordMatch(customerID, password)) return String.format("{\"Error\":true,\"Error Message\":\"Cannot get transaction history for User #%d: User ID and password do not match\"}", customerID);
            //creates a query to get transaction history, adds a limit if one is specified
            String historyQuery = String.format("SELECT TransactionID, SourceAccount, DestinationAccount, Amount, Status, TimeOfTransaction FROM Transactions WHERE CustomerID = %d ORDER BY TransactionID DESC", customerID);
            if(limit > 0) historyQuery += " LIMIT " + limit + ";";
            else historyQuery += ";";
            rs = stmt.executeQuery(historyQuery);
            ArrayList<String> transactionJSONs = new ArrayList<>();
            //creates a json object for each transaction
            while(rs.next()){
                int transactionID = rs.getInt(1);
                int sourceAccount = rs.getInt(2);
                //if there is no source account, the transaction is a deposit
                if(rs.wasNull()) transactionJSONs.add(String.format("{\"Transaction ID\":%d,\"Transaction Type\":\"Deposit\",\"Amount\":\"$%s\",\"Account ID\":%d,\"User ID\":%d,\"Status\":\"%s\",\"Time of Transaction\":\"%s\"}", transactionID, rs.getString(4), rs.getInt(3), customerID, rs.getString(5), rs.getString(6)));
                else{
                    int destinationAccount = rs.getInt(3);
                    //if there is no destination account, the transaction is a withdraw
                    if(rs.wasNull()) transactionJSONs.add(String.format("{\"Transaction ID\":%d,\"Transaction Type\":\"Withdraw\",\"Amount\":\"$%s\",\"Account ID\":%d,\"User ID\":%d,\"Status\":\"%s\",\"Time of Transaction\":\"%s\"}", transactionID, rs.getString(4), sourceAccount, customerID, rs.getString(5), rs.getString(6)));
                    //otherwise, it was a transfer
                    else transactionJSONs.add(String.format("{\"Transaction ID\":%d,\"Transaction Type\":\"Outgoing Transfer\",\"Amount\":\"$%s\",\"Account ID\":%d,\"Destination Account ID\":%d,\"User ID\":%d,\"Status\":\"%s\",\"Time of Transaction\":\"%s\"}", transactionID, rs.getString(4), sourceAccount, destinationAccount, customerID, rs.getString(5), rs.getString(6)));
                }
            }
            //creates output json, including transaction jsons in a json array
            String output = String.format("{\"Error\":false,\"User ID\":%d,\"Transactions\":[", customerID);
            for(String transactionJSON : transactionJSONs)
                output += transactionJSON + ",";
            if(transactionJSONs.size() > 0) return output.substring(0, output.length() - 1) + "]}";
            return output + "]}";
        }
        catch (SQLException e){
            e.printStackTrace();
            //returns json indicating an error
            return String.format("{\"Error\":true,\"Error Message\":\"Unable to get transaction history of User #%d\"}", customerID);
        }
    }

    /** 
    * Attempts to get an account's transaction history in order of most recent transaction first.
    *
    * @param  customerID The customer's user ID (must be valid).
    * @param  password   The customer's password (must match user ID).
    * @param  accountID  The account ID (must owned by the specified user, can be inactive).
    * @param  limit      The maximum number of transactions to be shown (if limit is 0 or less, all transactions will be shown).
    * @return            A JSON formatted string indicating the success of the request (includes the transaction history if successful).
    */
    public static String accountTransactionHistory(int customerID, String password, int accountID, int limit){
        try{
            //user ID and password must match
            if(!validString(password) || !passwordMatch(customerID, password)) return String.format("{\"Error\":true,\"Error Message\":\"Cannot get transaction history for Account #%d: User ID and password do not match\"}", accountID);
            //if the account does not exist, return json indicating the error
            if(!customersAccountExists(customerID, accountID)) return String.format("{\"Error\":true,\"Error Message\":\"Cannot get transaction history for Account #%d: User #%d does not have an account with this account number\"}", accountID, customerID);
            //creates a query to get transaction history, adds a limit if one is specified
            String historyQuery = String.format("SELECT TransactionID, SourceAccount, DestinationAccount, Amount, CustomerID, Status, TimeOfTransaction FROM Transactions WHERE SourceAccount = %d OR DestinationAccount = %d ORDER BY TransactionID DESC", accountID, accountID);
            if(limit > 0) historyQuery += " LIMIT " + limit + ";";
            else historyQuery += ";";
            rs = stmt.executeQuery(historyQuery);
            ArrayList<String> transactionJSONs = new ArrayList<>();
            //creates a json object for each transaction
            while(rs.next()){
                int transactionID = rs.getInt(1);
                int sourceAccount = rs.getInt(2);
                //if there is no source account, the transaction is a deposit
                if(rs.wasNull()) transactionJSONs.add(String.format("{\"Transaction ID\":%d,\"Transaction Type\":\"Deposit\",\"Amount\":\"$%s\",\"Account ID\":%d,\"User ID\":%d,\"Status\":\"%s\",\"Time of Transaction\":\"%s\"}", transactionID, rs.getString(4), accountID, rs.getInt(5), rs.getString(6), rs.getString(7)));
                else{
                    int destinationAccount = rs.getInt(3);
                    //if there is no destination account, the transaction is a withdraw
                    if(rs.wasNull()) transactionJSONs.add(String.format("{\"Transaction ID\":%d,\"Transaction Type\":\"Withdraw\",\"Amount\":\"$%s\",\"Account ID\":%d,\"User ID\":%d,\"Status\":\"%s\",\"Time of Transaction\":\"%s\"}", transactionID, rs.getString(4), accountID, rs.getInt(5), rs.getString(6), rs.getString(7)));
                    //if the account is the destination account, the transaction is an incoming transaction
                    else if(destinationAccount == accountID) transactionJSONs.add(String.format("{\"Transaction ID\":%d,\"Transaction Type\":\"Incoming Transfer\",\"Amount\":\"$%s\",\"Account ID\":%d,\"Source Account ID\":%d,\"User ID\":%d,\"Status\":\"%s\",\"Time of Transaction\":\"%s\"}", transactionID, rs.getString(4), accountID, sourceAccount, rs.getInt(5), rs.getString(6), rs.getString(7)));
                    //if the account is the source account, the transaction is an outgoing transaction
                    else if(sourceAccount == accountID) transactionJSONs.add(String.format("{\"Transaction ID\":%d,\"Transaction Type\":\"Outgoing Transfer\",\"Amount\":\"$%s\",\"Account ID\":%d,\"Destination Account ID\":%d,\"User ID\":%d,\"Status\":\"%s\",\"Time of Transaction\":\"%s\"}", transactionID, rs.getString(4), accountID, destinationAccount, rs.getInt(5), rs.getString(6), rs.getString(7)));
                }
            }
            //creates output json, including transaction jsons in a json array
            String output = String.format("{\"Error\":false,\"Account ID\":%d,\"Transactions\":[", accountID);
            for(String transactionJSON : transactionJSONs)
                output += transactionJSON + ",";
            if(transactionJSONs.size() > 0) return output.substring(0, output.length() - 1) + "]}";
            return output + "]}";
        }
        catch (SQLException e){
            e.printStackTrace();
            //returns json indicating an error
            return String.format("{\"Error\":true,\"Error Message\":\"Unable to get transaction history of Account #%d\"}", accountID);
        }
    }

    /** 
    * Parses the input JSON and calls the proper function.
    *
    * @param  inputJSONString The JSON String gathered from the API call.
    * @return                 The proper output JSON String based on the parameters of the input JSON.
    */
    public static String executeAPICall(String inputJSONString){
        try{
            //parses the input json to find the proper function
            Object parsedInputJSON = new JSONParser().parse(inputJSONString);
            JSONObject inputJSON = (JSONObject) parsedInputJSON;
            String function = (String) inputJSON.get("Function");
            if(function.equals("Register User")){
                //parses json for the proper parameters and calls the function
                String firstName = (String) inputJSON.get("First Name");
                String lastName = (String) inputJSON.get("Last Name");
                String password = (String) inputJSON.get("Password");
                String address = (String) inputJSON.get("Address");
                return registerUser(firstName, lastName, password, address);
            }
            else if(function.equals("Login")){
                //parses json for the proper parameters and calls the function
                Long customerID = (Long) inputJSON.get("User ID");
                String password = (String) inputJSON.get("Password");
                return login(customerID.intValue(), password);
            }
            else if(function.equals("Change Name")){
                //parses json for the proper parameters and calls the function
                Long customerID = (Long) inputJSON.get("User ID");
                String password = (String) inputJSON.get("Password");
                String newFirstName = (String) inputJSON.get("New First Name");
                String newLastName = (String) inputJSON.get("New Last Name");
                return changeName(customerID.intValue(), password, newFirstName, newLastName);
            }
            else if(function.equals("Change Address")){
                //parses json for the proper parameters and calls the function
                Long customerID = (Long) inputJSON.get("User ID");
                String password = (String) inputJSON.get("Password");
                String newAddress = (String) inputJSON.get("New Address");
                return changeAddress(customerID.intValue(), password, newAddress);
            }
            else if(function.equals("Change Password")){
                //parses json for the proper parameters and calls the function
                Long customerID = (Long) inputJSON.get("User ID");
                String oldPassword = (String) inputJSON.get("Old Password");
                String newPassword = (String) inputJSON.get("New Password");
                return changePassword(customerID.intValue(), oldPassword, newPassword);
            }
            else if(function.equals("Open Account")){
                //parses json for the proper parameters and calls the function
                Long customerID = (Long) inputJSON.get("User ID");
                String password = (String) inputJSON.get("Password");
                String accountName = (String) inputJSON.get("Account Name");
                String type = (String) inputJSON.get("Account Type");
                Double initialDeposit = (Double) inputJSON.get("Initial Deposit");
                return openAccount(customerID.intValue(), password, accountName, type, initialDeposit.doubleValue());
            }
            else if(function.equals("Change Account Name")){
                //parses json for the proper parameters and calls the function
                Long customerID = (Long) inputJSON.get("User ID");
                String password = (String) inputJSON.get("Password");
                Long accountID = (Long) inputJSON.get("Account ID");
                String newAccountName = (String) inputJSON.get("New Account Name");
                return changeAccountName(customerID.intValue(), password, accountID.intValue(), newAccountName);
            }
            else if(function.equals("Close Account")){
                //parses json for the proper parameters and calls the function
                Long customerID = (Long) inputJSON.get("User ID");
                String password = (String) inputJSON.get("Password");
                Long accountID = (Long) inputJSON.get("Account ID");
                return closeAccount(customerID.intValue(), password, accountID.intValue());
            }
            else if(function.equals("Withdraw")){
                //parses json for the proper parameters and calls the function
                Long customerID = (Long) inputJSON.get("User ID");
                String password = (String) inputJSON.get("Password");
                Long accountID = (Long) inputJSON.get("Account ID");
                Double amount = (Double) inputJSON.get("Amount");
                return withdraw(customerID.intValue(), password, accountID.intValue(), amount.doubleValue());
            }
            else if(function.equals("Deposit")){
                //parses json for the proper parameters and calls the function
                Long customerID = (Long) inputJSON.get("User ID");
                String password = (String) inputJSON.get("Password");
                Long accountID = (Long) inputJSON.get("Account ID");
                Double amount = (Double) inputJSON.get("Amount");
                return deposit(customerID.intValue(), password, accountID.intValue(), amount.doubleValue());
            }
            else if(function.equals("Transfer")){
                //parses json for the proper parameters and calls the function
                Long customerID = (Long) inputJSON.get("User ID");
                String password = (String) inputJSON.get("Password");
                Long sourceAccountID = (Long) inputJSON.get("Source Account ID");
                Long destinationAccountID = (Long) inputJSON.get("Destination Account ID");
                Double amount = (Double) inputJSON.get("Amount");
                return transfer(customerID.intValue(), password, sourceAccountID.intValue(), destinationAccountID.intValue(), amount.doubleValue());
            }
            else if(function.equals("User Info")){
                //parses json for the proper parameters and calls the function
                Long customerID = (Long) inputJSON.get("User ID");
                String password = (String) inputJSON.get("Password");
                return userInfo(customerID.intValue(), password);
            }
            else if(function.equals("User Account Summary")){
                //parses json for the proper parameters and calls the function
                Long customerID = (Long) inputJSON.get("User ID");
                String password = (String) inputJSON.get("Password");
                Boolean includeInactive = (Boolean) inputJSON.get("Include Inactive");
                return userAccountSummary(customerID.intValue(), password, includeInactive.booleanValue());
            }
            else if(function.equals("User Transaction History")){
                //parses json for the proper parameters and calls the function
                Long customerID = (Long) inputJSON.get("User ID");
                String password = (String) inputJSON.get("Password");
                Long limit = (Long) inputJSON.get("Limit");
                return userTransactionHistory(customerID.intValue(), password, limit.intValue());
            }
            else if(function.equals("Account Transaction History")){
                //parses json for the proper parameters and calls the function
                Long customerID = (Long) inputJSON.get("User ID");
                String password = (String) inputJSON.get("Password");
                Long accountID = (Long) inputJSON.get("Account ID");
                Long limit = (Long) inputJSON.get("Limit");
                return accountTransactionHistory(customerID.intValue(), password, accountID.intValue(), limit.intValue());
            }
            //if the function name was not recognized, returns json indicating an error
            else return "{\"Error\":true,\"Error Message\":\"Invalid JSON\"}";
        }
        catch (Exception e){
            return "{\"Error\":true,\"Error Message\":\"Invalid JSON\"}";
        }
    }

    /*
    **
    **
    HELPER METHODS
    **
    **
    */

    /** 
    * Checks whether a customer ID is valid.
    *
    * @param  customerID    A user ID.
    * @return               True if the customer exists and false if not.
    * @see    passwordMatch This function is not currently, its functionallity is included in passwordMatch.
    * @throws SQLException  If there is a database error.
    */
    public static boolean customerExists(int customerID) throws SQLException{
        String verify = String.format("SELECT COUNT(*) FROM Customers WHERE CustomerID = %d;", customerID);
        rs = stmt.executeQuery(verify);
        int users = 0;
        while(rs.next()) users = rs.getInt(1);
        if(users == 1) return true;
        return false;
    }

    /** 
    * Checks whether a customer ID and password match.
    *
    * @param  customerID   A user ID.
    * @param  password     A password.
    * @return              True if the user ID and password match and false if not.
    * @throws SQLException If there is a database error.
    */
    public static boolean passwordMatch(int customerID, String password) throws SQLException{
        String match = String.format("SELECT COUNT(*) FROM Customers WHERE CustomerID = %d AND Password = '%s';", customerID, password);
        rs = stmt.executeQuery(match);
        while(rs.next()){
            if(rs.getInt(1) == 1) return true;
        }
        return false;
    }

    /** 
    * Checks whether an account ID is valid.
    *
    * @param  accountID       An account ID.
    * @return                 True if the account exists and false if not.
    * @see    accountIsActive This function is not currently, its functionallity is included in accountIsActive.
    * @throws SQLException    If there is a database error.
    */
    public static boolean accountExists(int accountID) throws SQLException{
        String verify = String.format("SELECT COUNT(*) FROM Accounts WHERE AccountID = %d;", accountID);
        rs = stmt.executeQuery(verify);
        int accounts = 0;
        while(rs.next()) accounts = rs.getInt(1);
        if(accounts > 0) return true;
        return false;
    }

    /** 
    * Checks whether an account is active.
    *
    * @param  accountID    An account ID.
    * @return              True if the account is active and false if not.
    * @throws SQLException If there is a database error.
    */
    public static boolean accountIsActive(int accountID) throws SQLException{
        String verify = String.format("SELECT COUNT(*) FROM Accounts WHERE AccountID = %d AND Status = 'A';", accountID);
        rs = stmt.executeQuery(verify);
        int accounts = 0;
        while(rs.next()) accounts = rs.getInt(1);
        if(accounts > 0) return true;
        return false;
    }

    /** 
    * Checks whether a customer owns an account.
    *
    * @param  customerID   A user ID.
    * @param  accountID    An account ID.
    * @return              True if the account exists and is owned by the user and false if not.
    * @throws SQLException If there is a database error.
    */
    public static boolean customersAccountExists(int customerID, int accountID) throws SQLException{
        String verify = String.format("SELECT COUNT(*) FROM Accounts WHERE AccountID = %d AND CustomerID = %d;", accountID, customerID);
        rs = stmt.executeQuery(verify);
        int accounts = 0;
        while(rs.next()) accounts = rs.getInt(1);
        if(accounts > 0) return true;
        return false;
    }

    /** 
    * Checks whether a customer owns an account, and if the account is active.
    *
    * @param  customerID   A user ID.
    * @param  accountID    An account ID.
    * @return              True if the account exists, is active, and is owned by the user and false if not.
    * @throws SQLException If there is a database error.
    */
    public static boolean customersAccountIsActive(int customerID, int accountID) throws SQLException{
        String verify = String.format("SELECT COUNT(*) FROM Accounts WHERE AccountID = %d AND CustomerID = %d AND Status = 'A';", accountID, customerID);
        rs = stmt.executeQuery(verify);
        int accounts = 0;
        while(rs.next()) accounts = rs.getInt(1);
        if(accounts > 0) return true;
        return false;
    }

    /** 
    * Checks whether a customer has an active account with a specified name.
    *
    * @param  customerID   A user ID.
    * @param  accountName  An account name.
    * @return              True if the customer has an active account with that name and false if not.
    * @throws SQLException If there is a database error.
    */
    public static boolean accountNameExists(int customerID, String accountName) throws SQLException{
        String verify = String.format("SELECT COUNT(*) FROM Accounts WHERE CustomerID = %d AND AccountName = '%s' AND Status = 'A';", customerID, accountName);
        rs = stmt.executeQuery(verify);
        int accounts = 0;
        while(rs.next()) accounts = rs.getInt(1);
        if(accounts > 0) return true;
        return false;
    }

    /** 
    * Finds whether a string not empty and is valid (e.g does not contain apostrophes, quotes, backslashes, or semicolons) .
    *
    * @param  test A user ID.
    * @return      True if the string is valid and false if not.
    */
    public static boolean validString(String test){
        if(test.length() == 0) return false;
        for(char c : test.toCharArray())
            if(c == '\'' || c == '\"' || c == ';' || c == '\\') return false;
        return true;
    }

    /** 
    * Listens to the Intermediate Database Table: APICalls.
    * Upon update to this table, incoming API calls are executed and their outputs are put into the APIResponses table.
    *
    * @param args Not used
    */
    public static void main(String[] args) throws ClassNotFoundException, SQLException{
        //io cleanup for when program is terminated
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
        { 
            try{
                System.out.println();
                System.out.println("No Longer Listening");
                if(rs != null) rs.close();
                if(stmt != null) stmt.close();
                if(con != null) con.close();
                System.out.println("IO Cleanup Successful");
            }
            catch(SQLException e){
                System.out.println("IO Cleanup Failed");
            }
        }));

        //connects to the database
        System.out.println();
        System.out.println("Attempting Connection...");
        APIProcessing connection = new APIProcessing();
        System.out.println();
        System.out.println("Listening for API calls");
        System.out.println();

        //variables used in the listening loop
        String getLastApiID = "SELECT MAX(APIID) FROM APICalls;";
        rs = stmt.executeQuery(getLastApiID);
        int lastAPIID = -1;
        while(rs.next()) lastAPIID = rs.getInt(1);
        String getNewRequests = "";
        ArrayList<APIRequest> newRequests = new ArrayList<>();
        
        //listening forever
        while(true){
            try{
                newRequests.clear();
                getNewRequests = String.format("SELECT * FROM APICalls WHERE APIID > %d ORDER BY APIID;", lastAPIID);
                rs = stmt.executeQuery(getNewRequests); 

                //put all new API requests in an list and record the last API ID that was received
                while(rs.next()){
                    try{
                        int apiID = rs.getInt(1);
                        int apiKey = rs.getInt(2);
                        String inputJSON = rs.getString(3);
                        newRequests.add(new APIRequest(apiID, apiKey, inputJSON));
                        lastAPIID = apiID;

                        //received api call logged to console
                        System.out.printf("Received API Request #%d\n", apiID);
                    }
                    catch(Exception e){
                        System.out.println("Unexpected Error...");
                        e.printStackTrace();
                    }
                }

                //execute each of the api requests and update the intermediate database to have the output JSON
                for(APIRequest req : newRequests){
                    try{
                        String apiIncomplete = String.format("SELECT COUNT(*) FROM APIResponses WHERE APIID = %d;", req.apiID);

                        int completeCount = 0;
                        rs = stmt.executeQuery(apiIncomplete);
                        while(rs.next()) completeCount = rs.getInt(1);

                        //if there is already an entry in the APIResponses table for this request, skip it
                        if(completeCount > 0) System.out.printf("API Request #%d Was Already Serviced\n", req.apiID);
                        else{
                            //in progress api call logged to console
                            System.out.printf("Working on API Request #%d\n", req.apiID);

                            String insertOutputJSON = String.format("INSERT INTO APIResponses VALUES (%d, %d, '%s');", req.apiID, req.apiKey, executeAPICall(req.json));
                            stmt.execute(insertOutputJSON);

                            //completion of api call logged to console
                            System.out.printf("Completed API Request #%d\n", req.apiID);
                        }
                    }
                    catch(Exception e){
                        System.out.println("Unexpected Error...");
                        e.printStackTrace();
                    }
                }
            }
            catch(Exception e){
                System.out.println("Unexpected Error...");
                e.printStackTrace();
            }
        }
    }
}
