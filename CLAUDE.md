# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Shepherd is a Community Management Agent that uses the Embabel Agent framework to help manage GitHub developer communities. It monitors GitHub repositories for new issues, analyzes them using AI (Utility AI pattern), and can research the people who raise issues.

## Build & Run Commands

```bash
# Build the project
./mvnw compile

# Run tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=RepoIdTest

# Run a single test method
./mvnw test -Dtest=RepoIdTest#testFromUrl

# Run the application in interactive shell mode
./scripts/shell.sh

# Run an MCP server exposing tools over SSE
./scripts/mcp_server.sh
```

## Environment Variables

- `OPENAI_API_KEY` - Required for LLM functionality
- `GITHUB_PERSONAL_ACCESS_TOKEN` - Optional but recommended to avoid rate limiting; without it the application may hang

## Architecture

### Framework
Built on **Embabel Agent** (`embabel-agent-starter`) - a Kotlin/Spring Boot framework for building AI agents. Uses OpenAI by default (can switch to Anthropic by uncommenting dependency in pom.xml).

### Core Components

**Domain Layer** (`com.embabel.shepherd.domain`)
- `Issue` / `RaisableIssue` - GitHub issue representations with person relationships
- `Person` / `Profile` - User information and AI-researched profiles
- `Employer` - Company associations

**Agent Actions** (`com.embabel.shepherd.agent.IssueActions`)
- `@Action` annotated methods that form the Utility AI workflow
- `saveNewIssue` - Stores new issues, skips known ones
- `reactToNewIssue` - Generates AI first response with urgency/sentiment analysis
- `researchRaiser` - Researches unknown issue authors using web tools

**Services** (`com.embabel.shepherd.service`)
- `IssueReader` - Fetches issues from GitHub via kohsuke/github-api
- `Store` - Persistence layer using `MixinTemplate` (Drivine-based)

**Configuration** (`com.embabel.shepherd.conf`)
- `ShepherdProperties` - Type-safe configuration for LLM options, repository list, profile categories

### Data Flow
1. Shell command triggers issue scan for configured repositories
2. New issues are saved to Neo4j, existing ones skipped
3. AI generates response analyzing urgency and sentiment
4. Unknown issue raisers are researched via web tools

### Prompt Templates
Located in `src/main/resources/prompts/` with `.jinja` extension:
- `first_response.jinja` - Template for initial issue response
- `research_person.jinja` - Template for researching issue authors

### Database
Neo4j graph database for storing issues, people, and relationships. Test containers used for integration tests.

## Key Patterns

- **Utility AI**: Actions are scored and selected based on utility rather than explicit workflow
- **SpEL Preconditions**: `@Action(pre = ["spel:..."])` for conditional action execution
- **Delegation pattern**: `Issue by self` in Kotlin for interface delegation
