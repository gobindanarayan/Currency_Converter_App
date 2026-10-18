# Currency Converter (Java Swing + MySQL)

A small desktop learning project for Apache NetBeans. It converts amounts with either Frankfurter daily reference rates fetched over HTTPS or a fixed rate entered by the user. **The online feed is daily reference data, not a tick-by-tick live FX market feed.** The app can still perform fixed-rate conversions without internet access or a database.

## Requirements

- JDK 25 (recommended because this project targets Java 25), Apache NetBeans with Maven support, and Maven 3.9+ if running from a terminal.
- MySQL 8.0+ for persistent history and saved currency selections. MySQL is optional for conversion itself.
- Internet access only for the online rate mode and fetching a full currency list.

The source only uses standard Swing and Java `HttpClient` (included since Java 11). Jackson parses JSON, and the MySQL Connector/J Maven dependency provides JDBC. If your school lab only has JDK 17 or 21, change `maven.compiler.release` and the compiler plugin's `<release>` in `pom.xml` to that installed version; the source itself uses no Java 25-only feature. Keep Maven dependencies unchanged unless your installed Maven/JDK combination requires another compatible release.

## Project structure

```text
currency-converter/
├── config/app.properties.example   # safe template; copy to ignored app.properties
├── sql/schema.sql                  # database/user/tables
├── src/main/java/com/example/currencyconverter/
│   ├── Main.java                   # starts Swing on the event thread
│   ├── config/AppConfig.java       # local file + environment configuration
│   ├── db/DatabaseRepository.java  # JDBC prepared statements
│   ├── model/                      # rate and history records
│   ├── service/ExchangeRateService.java # HTTP + Jackson JSON
│   └── ui/ConverterFrame.java     # Swing screen and actions
├── .gitignore
├── pom.xml
└── README.md
```

## Set up MySQL

1. Start MySQL Server.
2. Open `sql/schema.sql` in MySQL Workbench, replace `replace_this_password` with your own strong local password, and run the script. Or from a terminal:

   ```sh
   mysql -u root -p < sql/schema.sql
   ```

   The script creates `currency_converter`, a least-privilege local application user, the `conversion_history` table, and the `app_settings` table. If you use a different MySQL host/user, edit `db.url` and `db.user` below. The example `GRANT` is for local development only; adjust the host and account policy for your environment.

3. Copy `config/app.properties.example` to `config/app.properties` and set `db.password` to the password you chose. The `.gitignore` excludes the local file. You can instead leave the password blank there and set `CURRENCY_DB_PASSWORD` in your operating system environment. Environment overrides are `CURRENCY_API_BASE_URL`, `CURRENCY_API_TIMEOUT_SECONDS`, `CURRENCY_DB_URL`, `CURRENCY_DB_USER`, and `CURRENCY_DB_PASSWORD`.

Do not commit actual credentials. If database settings are absent or MySQL is down, the app warns that history/settings are unavailable but still allows conversions.

## Open and run in NetBeans

1. Choose **File → Open Project** and select the folder containing `pom.xml`.
2. Let Maven resolve the Jackson and MySQL Connector/J dependencies (requires Maven Central access the first time).
3. Set the project Java platform to JDK 25 (or adjust both compiler release values as described above).
4. Run the project. `com.example.currencyconverter.Main` is configured as the entry point.

You can also run from a terminal in the project folder:

```sh
mvn clean compile
mvn exec:java
```

## Try the features

- Enter a positive amount and choose source/target currencies from the drop-downs. **Swap currencies** reverses the selection.
- Choose **Daily reference rate (online)** and press **Refresh rate** to display the current published observation for the selected pair. Press **Convert** to fetch and apply it. The source and observation date appear with the result.
- Choose **Fixed/manual rate (offline)**, enter the number of target currency units equal to one source unit, and press **Convert**. Example: with `USD` to `INR`, a manual rate of `83.25` means 1 USD = 83.25 INR. It does not silently reuse stale online rates.
- **Clear / reset** clears the amount and output and resets the manual rate. The history table shows the most recent 100 stored conversions. Selected currencies are also stored in `app_settings` when the database is available.
- If the network/API fails, use the clear error message and switch to manual mode. Unsupported pairs and missing rates are reported by the API as errors. Amounts must be positive and have at most six decimal places. Results round to two decimal places.

## How the pieces work

1. `ConverterFrame` draws Swing labels, buttons, currency drop-downs, and the history table. A `SwingWorker` performs slow HTTP and SQL work in the background so the window remains responsive.
2. `ExchangeRateService` builds an HTTPS request with Java's `HttpClient`, then uses Jackson's `ObjectMapper` to read the JSON `rate` and `date` fields. The URL can be changed in local configuration.
3. Java's `BigDecimal` multiplies the amount by the returned (or manually entered) rate. It avoids the usual binary floating-point rounding surprises for money-like values.
4. `DatabaseRepository` uses JDBC prepared statements to insert conversion records and read the latest history. The SQL schema stores the pair, amount, exact applied rate, converted amount, source, rate observation date, and timestamp. A separate settings table keeps the two selected currencies.

## Data source and limitations

This sample uses the public [Frankfurter API v2](https://frankfurter.dev/), with `/v2/rate/{base}/{quote}` for a pair and `/v2/currencies` for supported currencies. Its documentation describes daily observations from central banks and other official sources; publication times can vary, and weekends/holidays may leave the latest observation several days old. The displayed observation date is therefore important. It is suitable for learning, reference conversion, and historical-style estimates; it is not suitable for trading, payments, or guaranteed settlement quotes. This public endpoint does not require an API key. No API key is placed in source code.

The app has not been designed as financial software. MySQL persistence depends on the local server and schema having been set up. A stale cached rate is never used implicitly. The public API's currency coverage can change over time.

## Quick checks

- Online: select USD → EUR, refresh the rate, check the displayed observation date and convert a positive amount.
- Offline: disconnect from the internet, select manual mode, enter a positive rate, and convert.
- Input errors: try a blank, zero, negative, or nonnumeric amount, then a blank/zero manual rate.
- Database: with MySQL configured, make a conversion and confirm a row appears in `conversion_history`; restart and confirm history/selections return. Temporarily stop MySQL to check that the conversion still works while a warning explains history cannot be saved.
