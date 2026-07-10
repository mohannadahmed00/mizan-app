# Mizan - Product Specification

## Overview

Mizan is a gamified Android application that helps Muslims build consistency in worship and personal development through daily habit tracking, progress visualization, streaks, achievements, and friendly competition.

Unlike a traditional to-do list, Mizan is designed around recurring acts of worship and positive habits. Users earn points by completing daily tasks, monitor their consistency over time, and eventually participate in community leaderboards.

The application follows an offline-first architecture, storing user progress locally while being designed for future synchronization with a backend.

---

# Vision

Help users become more consistent in worship and positive habits through motivation, accountability, and measurable progress.

The application should encourage improvement without becoming overwhelming, focusing on long-term consistency rather than perfection.

---

# Goals

- Track recurring daily worship
- Track custom positive habits
- Encourage consistency through streaks
- Reward users with points
- Provide meaningful statistics
- Visualize progress over time
- Support offline usage
- Be easily extensible for cloud synchronization and social features

---

# Target Platform

Android

Minimum SDK:
TBD

Technology

- Kotlin
- Jetpack Compose
- Clean Architecture
- MVVM
- Koin
- Room
- Retrofit
- Coroutines
- StateFlow

---

# Core Concepts

## Task

A task represents a recurring activity.

Examples:

- Fajr Prayer
- Dhuhr Prayer
- Quran Reading
- Morning Adhkar
- Evening Adhkar
- Fast Monday
- Fast Thursday
- Read Islamic Book
- Exercise
- Drink Water

A task is only a definition.

It does NOT contain completion status.

### Properties

- id
- name
- description (optional)
- category
- points
- activeDays
- isActive
- icon (optional)
- color (optional)

---

## Task Completion

Represents a user's completion of a task on a specific day.

Each completion belongs to:

- one task
- one date
- one user (future backend)

Properties

- taskId
- completedDate

The existence of a record means the task was completed.

---

## Categories

Examples

- Prayer
- Quran
- Adhkar
- Fasting
- Charity
- Knowledge
- Health
- Productivity
- Custom

---

# Daily Flow

When the application opens:

1. Determine today's Gregorian date.
2. Retrieve the corresponding Hijri date.
3. If not cached:
    - download monthly Hijri calendar
    - store locally
4. Load today's active tasks.
5. Load today's completed task records.
6. Calculate today's progress.
7. Display dashboard.

---

# Task Scheduling

Each task defines the days on which it should appear.

Examples

Daily

```
Saturday
Sunday
Monday
Tuesday
Wednesday
Thursday
Friday
```

Monday only

```
Monday
```

Monday and Thursday

```
Monday
Thursday
```

Weekend only

```
Friday
Saturday
```

---

# Points System

Every task has a point value.

Example

| Task | Points |
|-------|----------|
| Fajr | 5 |
| Dhuhr | 5 |
| Asr | 5 |
| Maghrib | 5 |
| Isha | 5 |
| Quran | 10 |
| Morning Adhkar | 5 |
| Evening Adhkar | 5 |
| Monday Fast | 20 |

Daily points are calculated dynamically.

Weekly points are the sum of daily points.

Monthly points are the sum of completed tasks.

No score should be stored directly.

Scores are derived from completion records.

---

# Streaks

The application should calculate:

Current streak

Longest streak

Task streak

Examples

- Fajr completed 18 consecutive days
- Quran completed 42 consecutive days

---

# Statistics

The application should generate statistics including:

Daily completion

Weekly completion

Monthly completion

Completion percentage

Points earned

Average daily points

Task completion frequency

Most completed task

Least completed task

Current streak

Longest streak

---

# Dashboard

The home screen should display:

Today's Gregorian date

Today's Hijri date

Today's tasks

Completed tasks

Remaining tasks

Today's points

Daily completion percentage

Quick statistics

---

# Calendar

Future feature

Users should be able to browse previous days.

Selecting a day displays

- completed tasks
- earned points
- completion percentage

---

# Charts

The application should visualize progress.

Examples

Weekly points

Monthly points

Completion rate

Task consistency

Streak history

Possible chart types

- Line chart
- Bar chart
- Heatmap
- Calendar view

---

# Achievements

Future feature

Examples

First Prayer

Complete all prayers for one day

7 day streak

30 day streak

1000 points

Complete Quran task 100 times

---

# Leaderboards

Future feature

Daily leaderboard

Weekly leaderboard

Monthly leaderboard

Friends leaderboard

Global leaderboard

Rankings are calculated from earned points.

---

# Social Features

Future feature

Friends

Groups

Challenges

Shared streaks

Weekly competitions

Community events

---

# Notifications

Future feature

Prayer reminders

Habit reminders

Missed task reminders

Daily summary

Weekly summary

Achievement unlocked

---

# Offline First

The application should function without internet.

Only Hijri calendar synchronization requires networking.

Everything else should work locally.

---

# Local Storage

Room Database

Tables

## Tasks

Stores task definitions.

## TaskCompletions

Stores user completion history.

## HijriDates

Caches Gregorian/Hijri date mappings.

---

# Remote Services

Initially

Hijri Calendar API

Future

Authentication

Cloud Sync

Leaderboards

User Profiles

Achievements

---

# Domain Model

```
User
│
├── id
├── name
└── avatar

Task
│
├── id
├── name
├── category
├── points
├── activeDays
├── isActive
└── icon

TaskCompletion
│
├── id
├── taskId
├── userId (future)
└── completedDate

CompactDate
│
├── gregorian
└── hijri

SimpleDate
│
├── day
├── month
└── year
```

---

# Architecture

Clean Architecture

```
Presentation

↓

Domain

↓

Data
```

Presentation

- Compose
- ViewModel
- UI State

Domain

- UseCases
- Models
- Repository Contracts

Data

- Repository Implementations
- Room
- Retrofit
- DTO
- Mapper
- Local Data Source
- Remote Data Source

---

# Design Principles

- Offline first
- Single source of truth
- Immutable UI state
- Feature-based scalability
- Testable architecture
- Dependency injection
- Separation of concerns

---

# Future Roadmap

Phase 1

- Daily task tracking
- Local storage
- Hijri calendar
- Points
- Statistics

Phase 2

- Custom tasks
- Achievements
- Notifications
- Calendar history

Phase 3

- User accounts
- Cloud synchronization
- Backup
- Multi-device support

Phase 4

- Friends
- Challenges
- Leaderboards
- Honor Board

Phase 5

- AI-generated habit insights
- Personalized recommendations
- Smart reminders
- Community events

---

# Success Metrics

- Daily active users
- Weekly retention
- Average streak length
- Average daily completion rate
- Tasks completed per user
- User engagement with statistics
- Leaderboard participation

---

# Product Philosophy

Mizan is not intended to judge users or encourage unhealthy competition.

Its purpose is to inspire consistency, self-accountability, and gradual personal growth through positive reinforcement, insightful progress tracking, and a clean, distraction-free experience.