# Claude Desktop Setup for Onboarding Assistant

This guide explains how to configure Claude Desktop to use the CPG Onboarding Assistant, enabling hiring managers to check onboarding status, progress, and issues through natural language queries.

## Prerequisites

- **Claude Desktop** installed on your machine
- **CPG Server** running on localhost:8080 (or your deployment URL)
- Valid Claude Desktop subscription with MCP support

## Quick Start

### 1. Start the CPG Server

```bash
cd /path/to/cpg
./mvnw spring-boot:run
```

The server will start on `http://localhost:8080` with MCP endpoint at `/sse`.

### 2. Configure Claude Desktop

Add the CPG MCP server to your Claude Desktop configuration.

**macOS/Linux:** Edit `~/.config/claude/claude_desktop_config.json`

**Windows:** Edit `%APPDATA%\Claude\claude_desktop_config.json`

Add the following configuration:

```json
{
  "mcpServers": {
    "cpg-onboarding": {
      "url": "http://localhost:8080/sse",
      "transport": "sse"
    }
  }
}
```

For remote deployments, replace `localhost:8080` with your server URL.

### 3. Restart Claude Desktop

After updating the configuration, restart Claude Desktop to connect to the MCP server.

### 4. Verify Connection

In Claude Desktop, you should see the CPG tools available. Try asking:

> "What onboarding tools are available?"

Claude should list the available tools including `find_onboarding_status`, `get_my_onboardings`, etc.

## Available Tools

### Status & Progress Tools

| Tool | Description | Example Query |
|------|-------------|---------------|
| `find_onboarding_status` | Find status by candidate name or ID | "What's John Smith's onboarding status?" |
| `get_my_onboardings` | List all active onboardings | "Show me all active onboardings" |
| `get_onboarding_progress` | Get detailed step-by-step progress | "Show me the detailed progress for Jane Doe" |
| `get_estimated_completion` | Get estimated completion date | "When will Bob's onboarding be complete?" |

### Issue & Analysis Tools

| Tool | Description | Example Query |
|------|-------------|---------------|
| `get_onboarding_issues` | Check for blockers and issues | "Are there any issues with Alice's onboarding?" |
| `get_ai_analysis_summary` | Get AI background check analysis | "What did the AI analysis find for John?" |
| `get_recent_activity` | View recent activity | "What's happened recently in Jane's onboarding?" |

### Search Tools

| Tool | Description | Example Query |
|------|-------------|---------------|
| `search_candidates` | Search by name, ID, position, etc. | "Find all candidates in Engineering" |

## Available Prompts

Prompts provide pre-built analysis templates:

| Prompt | Description | How to Use |
|--------|-------------|------------|
| `onboarding_dashboard` | Dashboard view of all onboardings | "Give me an onboarding dashboard" |
| `candidate_deep_dive` | Detailed analysis of one candidate | "Do a deep dive on John Smith's onboarding" |
| `troubleshoot_onboarding` | Diagnose stuck onboardings | "Help me troubleshoot Jane Doe's onboarding" |

## Example Conversations

### Checking a Candidate's Status

**You:** What's the status of John Smith's onboarding?

**Claude:** John Smith (CAND-12345) is currently in the Background Check phase of onboarding.

📊 **Progress: 38% complete (5 of 13 steps)**

| Phase | Status |
|-------|--------|
| Offer & Validation | ✅ Complete |
| Background Check | 🔄 In Progress |
| IT Provisioning | ⏳ Pending |
| HR Documentation | ⏳ Pending |
| Completion | ⏳ Pending |

**Estimated Completion:** February 22, 2026

The AI background check analysis is currently in progress. No issues detected so far.

---

### Viewing Your Dashboard

**You:** Show me my onboarding dashboard

**Claude:** Here's your current onboarding dashboard:

📋 **Active Onboardings: 4**

| Candidate | Position | Progress | Phase | Issues |
|-----------|----------|----------|-------|--------|
| John Smith | Engineer | 38% | Background Check | None |
| Jane Doe | Designer | 62% | IT Provisioning | None |
| Bob Wilson | Analyst | 23% | Background Check | ⚠️ 1 issue |
| Alice Chen | PM | 85% | Completion | None |

**Attention Needed:**
- **Bob Wilson**: AI analysis flagged findings requiring HR review

---

### Investigating Issues

**You:** What's wrong with Bob Wilson's onboarding?

**Claude:** I found 1 issue affecting Bob Wilson's onboarding:

🟡 **MEDIUM: AI Analysis Flagged**
- **Description**: Background check findings require HR review
- **Risk Score**: 45/100
- **Recommendation**: REVIEW
- **Key Findings**:
  - Employment gap of 18 months (2022-2023)
  - Education verification pending

**Suggested Action:** HR must review the background check findings in the "review-background-results" step. Contact hr-team@company.com to expedite.

---

### Getting AI Analysis Details

**You:** What did the AI find in Jane Doe's background check?

**Claude:** Here's the AI analysis for Jane Doe:

✅ **Analysis Complete - Approved**

- **Risk Score**: 8/100 (Low)
- **Recommendation**: APPROVE
- **Summary**: Clean background check with no adverse findings

The candidate passed the AI analysis and was automatically approved. No HR review required.

## Troubleshooting

### Claude doesn't see the tools

1. Verify the CPG server is running: `curl http://localhost:8080/actuator/health`
2. Check Claude Desktop logs for MCP connection errors
3. Ensure the config file path is correct for your OS
4. Try restarting Claude Desktop

### Connection errors

1. Check firewall settings
2. Verify the URL in config matches your server
3. For HTTPS deployments, ensure certificates are valid

### Tools return errors

1. Check CPG server logs for errors
2. Verify the onboarding process graph is loaded
3. Ensure there are active onboarding instances to query

## Security Considerations

1. **Network Security**: Use HTTPS in production
2. **Authentication**: Consider adding API key authentication for production use
3. **Data Privacy**: Be aware that candidate PII is transmitted through the MCP connection
4. **Audit Logging**: All MCP tool invocations are logged by the CPG server

## Support

For issues or questions:
- Check CPG server logs at `logs/cpg.log`
- Review MCP server configuration in `application.yml`
- Submit issues at https://github.com/iHelio/cpg-project/issues
