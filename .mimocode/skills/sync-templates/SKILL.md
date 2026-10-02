---
name: sync-templates
description: "Apply the same HTML/CSS change across multiple Thymeleaf templates. Use when a change needs to be consistent across all pages."
---

# Sync Templates Skill

Apply a consistent change across multiple Thymeleaf templates in the 优品汇 project.

## When to Use

- Adding/modifying navigation elements (header, category bar, footer)
- Updating cart badge, user dropdown, or other shared components
- Changing CSS classes that appear in multiple templates
- Any change that must be consistent across all pages

## Template Inventory

### Pages using base.html (Thymeleaf inheritance)
- None currently (all pages have inline headers)

### Pages with inline header (must update manually)
1. `index.html` - Homepage
2. `category.html` - Category pages
3. `search.html` - Search results
4. `product.html` - Product detail
5. `cart.html` - Shopping cart
6. `checkout.html` - Checkout
7. `login.html` - Login/Register
8. `upload.html` - Product upload
9. `user.html` - User profile
10. `my-orders.html` - My orders
11. `order-detail.html` - Order detail
12. `seller-orders.html` - Seller orders
13. `favorites.html` - Favorites
14. `messages.html` - Chat (custom layout)
15. `orders.html` - Order management

### Pages with separate header
- `admin/dashboard.html` - Admin dashboard (independent style)
- `admin/products.html` - Admin products
- `admin/users.html` - Admin users
- `admin/login.html` - Admin login

## Workflow

### Step 1: Identify the change
Determine exactly what needs to be changed (HTML structure, CSS class, attribute, etc.)

### Step 2: Find all affected templates
Use grep to find all files containing the target pattern:
```powershell
Get-ChildItem -Path "E:\mimo-worker\javaweb\src\main\resources\templates" -Filter "*.html" -Recurse | Select-String -Pattern "<pattern>" | Select-Object Path
```

### Step 3: Apply change to each file
For each affected file:
1. Read the file
2. Find the exact location to change
3. Apply the edit
4. Verify the change was applied correctly

### Step 4: Verify consistency
After all changes, verify no files were missed:
```powershell
Get-ChildItem -Path "E:\mimo-worker\javaweb\src\main\resources\templates" -Filter "*.html" -Recurse | Select-String -Pattern "<old_pattern>" | Measure-Object
```
Should return 0 if all files were updated.

## Common Patterns

### Pattern 1: Add cart badge to header
All pages with `<a href="/cart" class="btn btn-ghost">` need:
```html
<a href="/cart" class="btn btn-ghost" aria-label="购物车" style="position:relative;">
  <i class="fas fa-shopping-cart"></i>
  <span class="cart-count-badge" style="display:none">0</span>
</a>
```

### Pattern 2: Add category bar
All pages need the category bar with consistent categories.

### Pattern 3: Update footer
All pages need consistent footer structure.

## Notes

- `messages.html` has a completely different layout (no base.html structure)
- `admin/*.html` pages have independent styles and don't use the main style.css
- Always verify changes work on both desktop and mobile views
