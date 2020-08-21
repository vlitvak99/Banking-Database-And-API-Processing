/**
*
* @author Vlad Litvak
* Created: 03.15.2020
* @since 05.06.2020
* @version 4.3
*
*/

SET GLOBAL time_zone = '+00:00';

DROP TRIGGER IF EXISTS NewTransaction;
DROP TABLE IF EXISTS Transactions;
DROP TABLE IF EXISTS Accounts;
DROP TABLE IF EXISTS Customers;
DROP TABLE IF EXISTS APICalls;
DROP TABLE IF EXISTS APIResponses;

CREATE TABLE IF NOT EXISTS Customers(
	CustomerID INTEGER PRIMARY KEY AUTO_INCREMENT,
	FirstName VARCHAR(30) NOT NULL,
	LastName VARCHAR(30) NOT NULL,
	Password VARCHAR(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
	Address VARCHAR(100) NOT NULL
);

ALTER TABLE Customers AUTO_INCREMENT = 10001;

CREATE TABLE IF NOT EXISTS Accounts(
	AccountID INTEGER PRIMARY KEY AUTO_INCREMENT,
	AccountName VARCHAR(50) NOT NULL,
	Balance DECIMAL(14,2) NOT NULL DEFAULT 0,
	Type CHAR NOT NULL,
	Status CHAR NOT NULL DEFAULT 'A',
	CustomerID INTEGER NOT NULL,
	FOREIGN KEY(CustomerID) REFERENCES Customers(CustomerID) ON DELETE CASCADE,
	CHECK((Type = 'C' OR Type = 'S') AND (Status = 'A' OR Status = 'I') AND Balance >= 0)
);

ALTER TABLE Accounts AUTO_INCREMENT = 100001;
ALTER TABLE Accounts ADD INDEX (CustomerID);

/**
* No SourceAccount means Deposit into DestinationAccount
* No DestinationAccount means Withdraw from SourceAccount
*/
CREATE TABLE IF NOT EXISTS Transactions(
	TransactionID INTEGER PRIMARY KEY AUTO_INCREMENT,
	SourceAccount INTEGER DEFAULT NULL,
	DestinationAccount INTEGER DEFAULT NULL,
	CustomerID INTEGER NOT NULL,
	Amount DECIMAL(14,2) NOT NULL,
	Status VARCHAR(255) DEFAULT 'Pending',
	TimeOfTransaction TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
	FOREIGN KEY(SourceAccount) REFERENCES Accounts(AccountID) ON DELETE CASCADE,
	FOREIGN KEY(DestinationAccount) REFERENCES Accounts(AccountID) ON DELETE CASCADE,
	FOREIGN KEY(CustomerID) REFERENCES Customers(CustomerID) ON DELETE CASCADE,
	CHECK(AMOUNT > 0 AND SourceAccount <> DestinationAccount)
);

ALTER TABLE Transactions AUTO_INCREMENT = 100000001;
ALTER TABLE Transactions ADD INDEX (SourceAccount);
ALTER TABLE Transactions ADD INDEX (DestinationAccount);
ALTER TABLE Transactions ADD INDEX (CustomerID);

CREATE TABLE IF NOT EXISTS APICalls(
	APIID INTEGER PRIMARY KEY AUTO_INCREMENT,
	APIKey INTEGER NOT NULL,
	InputJSON VARCHAR(1000) NOT NULL
);

ALTER TABLE APICalls AUTO_INCREMENT = 1;

CREATE TABLE IF NOT EXISTS APIResponses(
	APIID INTEGER PRIMARY KEY,
	APIKey INTEGER NOT NULL,
	OutputJSON VARCHAR(65535) NOT NULL
);

DELIMITER @

CREATE TRIGGER NewTransaction
BEFORE INSERT ON Transactions
FOR EACH ROW
BEGIN
	IF (NEW.SourceAccount IS NULL AND NEW.DestinationAccount IS NULL) THEN
		SET NEW.Status = 'Transaction Failed: No Source or Destination Accounts';
	ELSEIF (NEW.SourceAccount IS NULL) THEN
		IF ((SELECT MAX(Balance) FROM Accounts WHERE AccountID = NEW.DestinationAccount) + NEW.Amount > 999999999999.99) THEN
			SET NEW.Status = 'Transaction Failed: Account Balance Cannot Exceed $999,999,999,999.99';
		ELSE
			UPDATE Accounts SET Balance = Balance + NEW.Amount WHERE AccountID = NEW.DestinationAccount;
			SET NEW.Status = 'Transaction Complete';
		END IF;
	ELSEIF (NEW.DestinationAccount IS NULL) THEN
		IF ((SELECT MAX(Balance) FROM Accounts WHERE AccountID = NEW.SourceAccount) - NEW.Amount < 0) THEN
			SET NEW.Status = 'Transaction Failed: Insufficient Funds';
		ELSE
			UPDATE Accounts SET Balance = Balance - NEW.Amount WHERE AccountID = NEW.SourceAccount;
			SET NEW.Status = 'Transaction Complete';
		END IF;
	ELSE
		IF ((SELECT MAX(Balance) FROM Accounts WHERE AccountID = NEW.SourceAccount) - NEW.Amount < 0) THEN
			SET NEW.Status = 'Transaction Failed: Insufficient Funds';
		ELSEIF ((SELECT MAX(Balance) FROM Accounts WHERE AccountID = NEW.DestinationAccount) + NEW.Amount > 999999999999.99) THEN
			SET NEW.Status = 'Transaction Failed: Account Balance Cannot Exceed $999,999,999,999.99';
		ELSE
			UPDATE Accounts SET Balance = Balance + NEW.Amount WHERE AccountID = NEW.DestinationAccount;
			UPDATE Accounts SET Balance = Balance - NEW.Amount WHERE AccountID = NEW.SourceAccount;
			SET NEW.Status = 'Transaction Complete';
		END IF;
	END IF;
END;@

DELIMITER ;
