package com.example.exdemo.exdemo;

import java.sql.*;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.List;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;

public class LibraryApplication {

	private static final String DB_URL = "jdbc:sqlite:library.db";
    private static Connection conn = null;
    private static Scanner scanner = new Scanner(System.in);
    private static User currentUser = null;

    public static void main(String[] args) {
        try {
            conn = DriverManager.getConnection(DB_URL);
            createTables();

            while (true) {
                if (currentUser == null) {
                    showLoginMenu();
                } else {
                    showMainMenu();
                }
            }
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        } finally {
            try {
                if (conn != null) {
                    conn.close();
                }
                scanner.close();
            } catch (SQLException e) {
                System.out.println("Error closing database connection: " + e.getMessage());
            }
        }
    }

    private static void createTables() throws SQLException {
        Statement stmt = conn.createStatement();

        stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                     "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                     "username TEXT UNIQUE NOT NULL," +
                     "password TEXT NOT NULL," +
                     "is_librarian BOOLEAN NOT NULL DEFAULT 0)");

        stmt.execute("CREATE TABLE IF NOT EXISTS books (" +
                     "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                     "title TEXT NOT NULL," +
                     "author TEXT NOT NULL," +
                     "total_copies INTEGER NOT NULL)");

        stmt.execute("CREATE TABLE IF NOT EXISTS loans (" +
                     "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                     "book_id INTEGER," +
                     "user_id INTEGER," +
                     "borrow_date TEXT NOT NULL," +
                     "due_date TEXT NOT NULL," +
                     "return_date TEXT)");

        stmt.close();
    }

    private static void showLoginMenu() throws SQLException {
        System.out.println("\n--- Login Menu ---");
        System.out.println("1. Login");
        System.out.println("2. Register");
        System.out.println("3. Exit");
        System.out.print("Enter your choice: ");

        int choice = scanner.nextInt();
        scanner.nextLine(); // Consume newline

        switch (choice) {
            case 1:
                login();
                break;
            case 2:
                register();
                break;
            case 3:
                System.out.println("Thank you !");
                System.exit(0);
            default:
                System.out.println("Invalid choice. Please try again.");
        }
    }

    private static void showMainMenu() throws SQLException {
        System.out.println("\n--- Library Management System ---");
        System.out.println("1. Search Books");
        System.out.println("2. Borrow Book");
        System.out.println("3. Return Book");
        System.out.println("4. View My Borrowed Books");
        System.out.println("5. Get Book Recommendations");
        if (currentUser.isLibrarian) {
            System.out.println("6. Add Book");
            System.out.println("7. View All Borrowed Books");
        }
        System.out.println("8. Logout");
        System.out.print("Enter your choice: ");

        int choice = scanner.nextInt();
        scanner.nextLine(); // Consume newline

        switch (choice) {
            case 1:
                searchBooks();
                break;
            case 2:
                borrowBook();
                break;
            case 3:
                returnBook();
                break;
            case 4:
                viewMyBorrowedBooks();
                break;
            case 5:
                getRecommendations();
                break;
            case 6:
                if (currentUser.isLibrarian) addBook();
                else System.out.println("Invalid choice.");
                break;
            case 7:
                if (currentUser.isLibrarian) viewAllBorrowedBooks();
                else System.out.println("Invalid choice.");
                break;
            case 8:
                currentUser = null;
                System.out.println("Logged out successfully.");
                break;
            default:
                System.out.println("Invalid choice. Please try again.");
        }
    }

    private static void login() throws SQLException {
        System.out.print("Enter username: ");
        String username = scanner.nextLine();
        System.out.print("Enter password: ");
        String password = scanner.nextLine();

        PreparedStatement pstmt = conn.prepareStatement(
            "SELECT * FROM users WHERE username = ? AND password = ?");
        pstmt.setString(1, username);
        pstmt.setString(2, hashPassword(password));
        ResultSet rs = pstmt.executeQuery();

        if (rs.next()) {
            currentUser = new User(rs.getInt("id"), rs.getString("username"), rs.getBoolean("is_librarian"));
            System.out.println("Login successful. Welcome, " + currentUser.username + "!");
        } else {
            System.out.println("Invalid username or password.");
        }

        rs.close();
        pstmt.close();
    }

    private static void register() throws SQLException {
        System.out.print("Enter username: ");
        String username = scanner.nextLine();
        System.out.print("Enter password: ");
        String password = scanner.nextLine();
        System.out.print("Are you a librarian? (y/n): ");
        boolean isLibrarian = scanner.nextLine().equalsIgnoreCase("y");

        PreparedStatement pstmt = conn.prepareStatement(
            "INSERT INTO users (username, password, is_librarian) VALUES (?, ?, ?)");
        pstmt.setString(1, username);
        pstmt.setString(2, hashPassword(password));
        pstmt.setBoolean(3, isLibrarian);

        try {
            pstmt.executeUpdate();
            System.out.println("Registration successful. Please login.");
        } catch (SQLException e) {
            if (e.getMessage().contains("UNIQUE constraint failed")) {
                System.out.println("Username already exists. Please choose a different username.");
            } else {
                throw e;
            }
        }

        pstmt.close();
    }

    private static void addBook() throws SQLException {
        System.out.print("Enter title: ");
        String title = scanner.nextLine();
        System.out.print("Enter author: ");
        String author = scanner.nextLine();
        System.out.print("Enter number of copies: ");
        int totalCopies = scanner.nextInt();
        scanner.nextLine(); // Consume newline

        PreparedStatement pstmt = conn.prepareStatement(
            "INSERT INTO books (title, author, total_copies) VALUES (?, ?, ?)");
        pstmt.setString(1, title);
        pstmt.setString(2, author);
        pstmt.setInt(3, totalCopies);
        pstmt.executeUpdate();
        pstmt.close();

        System.out.println("Book added successfully!");
    }

    private static void searchBooks() throws SQLException {
        System.out.print("Enter search term (title or author): ");
        String searchTerm = scanner.nextLine();

        PreparedStatement pstmt = conn.prepareStatement(
            "SELECT * FROM books WHERE title LIKE ? OR author LIKE ?");
        pstmt.setString(1, "%" + searchTerm + "%");
        pstmt.setString(2, "%" + searchTerm + "%");

        ResultSet rs = pstmt.executeQuery();
        while (rs.next()) {
            int bookId = rs.getInt("id");
            int totalCopies = rs.getInt("total_copies");
            int availableCopies = totalCopies - getBookLoanCount(bookId);
            System.out.printf("ID: %d, Title: %s, Author: %s, Available Copies: %d/%d\n",
                bookId, rs.getString("title"), rs.getString("author"),
                availableCopies, totalCopies);
        }

        rs.close();
        pstmt.close();
    }

    private static int getBookLoanCount(int bookId) throws SQLException {
        PreparedStatement pstmt = conn.prepareStatement(
            "SELECT COUNT(*) as loan_count FROM loans WHERE book_id = ? AND return_date IS NULL");
        pstmt.setInt(1, bookId);
        ResultSet rs = pstmt.executeQuery();
        int loanCount = rs.getInt("loan_count");
        rs.close();
        pstmt.close();
        return loanCount;
    }

    private static void borrowBook() throws SQLException {
        System.out.print("Enter book ID to borrow: ");
        int bookId = scanner.nextInt();
        scanner.nextLine(); // Consume newline

        PreparedStatement pstmt = conn.prepareStatement("SELECT total_copies FROM books WHERE id = ?");
        pstmt.setInt(1, bookId);
        ResultSet rs = pstmt.executeQuery();

        if (rs.next()) {
            int totalCopies = rs.getInt("total_copies");
            int loanCount = getBookLoanCount(bookId);

            if (loanCount < totalCopies) {
                LocalDate borrowDate = LocalDate.now();
                LocalDate dueDate = borrowDate.plusDays(14); // 2 weeks loan period
                PreparedStatement loanStmt = conn.prepareStatement(
                    "INSERT INTO loans (book_id, user_id, borrow_date, due_date) VALUES (?, ?, ?, ?)");
                loanStmt.setInt(1, bookId);
                loanStmt.setInt(2, currentUser.id);
                loanStmt.setString(3, borrowDate.toString());
                loanStmt.setString(4, dueDate.toString());
                loanStmt.executeUpdate();
                loanStmt.close();

                System.out.println("Book borrowed successfully! Due date: " + dueDate);
            } else {
                System.out.println("Sorry, all copies of this book are currently borrowed.");
            }
        } else {
            System.out.println("Book not found.");
        }

        rs.close();
        pstmt.close();
    }

    private static void returnBook() throws SQLException {
        System.out.print("Enter book ID to return: ");
        int bookId = scanner.nextInt();
        scanner.nextLine(); // Consume newline

        PreparedStatement pstmt = conn.prepareStatement(
            "UPDATE loans SET return_date = ? " +
            "WHERE book_id = ? AND user_id = ? AND return_date IS NULL");
        pstmt.setString(1, LocalDate.now().toString());
        pstmt.setInt(2, bookId);
        pstmt.setInt(3, currentUser.id);
        int updatedRows = pstmt.executeUpdate();

        if (updatedRows > 0) {
            System.out.println("Book returned successfully!");
        } else {
            System.out.println("Book not found or already returned.");
        }

        pstmt.close();
    }

    private static void viewMyBorrowedBooks() throws SQLException {
        PreparedStatement pstmt = conn.prepareStatement(
            "SELECT book_id, borrow_date, due_date FROM loans " +
            "WHERE user_id = ? AND return_date IS NULL");
        pstmt.setInt(1, currentUser.id);
        ResultSet rs = pstmt.executeQuery();

        System.out.println("Your borrowed books:");
        while (rs.next()) {
            int bookId = rs.getInt("book_id");
            String bookDetails = getBookDetails(bookId);
            System.out.printf("%s, Borrowed: %s, Due: %s\n",
                bookDetails, rs.getString("borrow_date"), rs.getString("due_date"));
        }

        rs.close();
        pstmt.close();
    }

    private static String getBookDetails(int bookId) throws SQLException {
        PreparedStatement pstmt = conn.prepareStatement("SELECT title, author FROM books WHERE id = ?");
        pstmt.setInt(1, bookId);
        ResultSet rs = pstmt.executeQuery();
        String details = rs.next() ? "Title: " + rs.getString("title") + ", Author: " + rs.getString("author") : "Unknown Book";
        rs.close();
        pstmt.close();
        return details;
    }

    private static void getRecommendations() throws SQLException {
        PreparedStatement pstmt = conn.prepareStatement(
            "SELECT DISTINCT book_id FROM loans WHERE user_id = ?");
        pstmt.setInt(1, currentUser.id);
        ResultSet rs = pstmt.executeQuery();

        List<String> borrowedAuthors = new ArrayList<>();
        while (rs.next()) {
            int bookId = rs.getInt("book_id");
            String author = getBookAuthor(bookId);
            if (!borrowedAuthors.contains(author)) {
                borrowedAuthors.add(author);
            }
        }
        rs.close();
        pstmt.close();

        if (borrowedAuthors.isEmpty()) {
            System.out.println("No borrowing history found. Unable to generate recommendations.");
            return;
        }

        System.out.println("Recommended books:");
        for (String author : borrowedAuthors) {
            pstmt = conn.prepareStatement(
                "SELECT * FROM books WHERE author = ? AND id NOT IN " +
                "(SELECT book_id FROM loans WHERE user_id = ?) LIMIT 2");
            pstmt.setString(1, author);
            pstmt.setInt(2, currentUser.id);
            rs = pstmt.executeQuery();

            while (rs.next()) {
                System.out.printf("Title: %s, Author: %s\n",
                    rs.getString("title"), rs.getString("author"));
            }
            rs.close();
            pstmt.close();
        }
    }

    private static String getBookAuthor(int bookId) throws SQLException {
        PreparedStatement pstmt = conn.prepareStatement("SELECT author FROM books WHERE id = ?");
        pstmt.setInt(1, bookId);
        ResultSet rs = pstmt.executeQuery();
        String author = rs.next() ? rs.getString("author") : "Unknown";
        rs.close();
        pstmt.close();
        return author;
    }

    private static void viewAllBorrowedBooks() throws SQLException {
        PreparedStatement pstmt = conn.prepareStatement(
            "SELECT book_id, user_id, borrow_date, due_date FROM loans WHERE return_date IS NULL");
        ResultSet rs = pstmt.executeQuery();

        System.out.println("All borrowed books:");
        while (rs.next()) {
            int bookId = rs.getInt("book_id");
            int userId = rs.getInt("user_id");
            String bookDetails = getBookDetails(bookId);
            String username = getUserName(userId);
            System.out.printf("%s, Borrower: %s, Borrowed: %s, Due: %s\n",
                bookDetails, username, rs.getString("borrow_date"), rs.getString("due_date"));
        }

        rs.close();
        pstmt.close();
    }

    private static String getUserName(int userId) throws SQLException {
        PreparedStatement pstmt = conn.prepareStatement("SELECT username FROM users WHERE id = ?");
        pstmt.setInt(1, userId);
        ResultSet rs = pstmt.executeQuery();
        String username = rs.next() ? rs.getString("username") : "Unknown User";
        rs.close();
        pstmt.close();
        return username;
    }

    private static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashedPassword = md.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashedPassword) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }

    private static class User {
        int id;
        String username;
        boolean isLibrarian;

        User(int id, String username, boolean isLibrarian) {
            this.id = id;
            this.username = username;
            this.isLibrarian = isLibrarian;
        }
    }
}
