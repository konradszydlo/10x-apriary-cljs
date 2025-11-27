# AI Rules for 

Apriary Application

Apriary Summary is an MVP web application designed to automate the process of creating summaries of apiary work. The application is aimed at owners of small apiaries who need a quick and efficient way to document the history of work performed on hives.

## Tech Stack

- Biff
- Clojure 1.12
- XTDB 1.24
- Tailwind 4

## Project Structure

When introducing changes to the project, always follow the directory structure below:

- `./src` - source code
- `./src/com/apriary/pages` - Biff pages
- `./src/com/apriary/middleware.clj` - Biff middleware
- `./src/com//apriary/schema.clj` - XTDB schema definitions
- `./resources/public` - public assets

When modifying the directory structure, always update this section.

## Coding practices

### Guidelines for clean code

- Use feedback from clj-kondo linter to improve the code when making changes.
- Prioritize error handling and edge cases.
- Handle errors and edge cases at the beginning of functions.
- Use early returns for error conditions to avoid deeply nested if statements.
- Place the happy path last in the function for improved readability.
- Avoid unnecessary else statements; use when pattern instead.
- Use guard clauses to handle preconditions and invalid states early.
- Implement proper error logging and user-friendly error messages.
- Consider using custom error types for consistent error handling.

## Frontend

### General Guidelines

Use htmx for interactivity and dynamic content loading.

### Guidelines for Styling

#### Tailwind

- Use the @layer directive to organize styles into components, utilities, and base layers
- Use arbitrary values with square brackets (e.g., w-[123px]) for precise one-off designs
- Implement the Tailwind configuration file for customizing theme, plugins, and variants
- Leverage the theme() function in CSS for accessing Tailwind theme values
- Implement dark mode with the dark: variant
- Use responsive variants (sm:, md:, lg:, etc.) for adaptive designs
- Leverage state variants (hover:, focus-visible:, active:, etc.) for interactive elements

### Guidelines for Accessibility

#### ARIA Best Practices

- Use ARIA landmarks to identify regions of the page (main, navigation, search, etc.)
- Apply appropriate ARIA roles to custom interface elements that lack semantic HTML equivalents
- Set aria-expanded and aria-controls for expandable content like accordions and dropdowns
- Use aria-live regions with appropriate politeness settings for dynamic content updates
- Implement aria-hidden to hide decorative or duplicative content from screen readers
- Apply aria-label or aria-labelledby for elements without visible text labels
- Use aria-describedby to associate descriptive text with form inputs or complex elements
- Implement aria-current for indicating the current item in a set, navigation, or process
- Avoid redundant ARIA that duplicates the semantics of native HTML elements

### Backend and Database

- Use XTDB for database service
- Use Malli schemas to validate data exchanged with the backend.
- use `parse-uuid` to parse UUID strings into UUID objects.
- Use `:db/doc-type` attribute in XTDB schema to define document types
- Use `:db/op` like `:delete`, `:update` for database operations
