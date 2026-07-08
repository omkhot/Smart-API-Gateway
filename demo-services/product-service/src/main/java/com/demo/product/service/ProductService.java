package com.demo.product.service;

import com.demo.product.exception.InsufficientStockException;
import com.demo.product.exception.ResourceNotFoundException;
import com.demo.product.model.Product;
import com.demo.product.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    @Autowired
    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }

    public Product createProduct(Product product) {
        return productRepository.save(product);
    }

    public Product updateProduct(Long id, Product updatedProduct) {
        Product existing = getProductById(id);
        existing.setName(updatedProduct.getName());
        existing.setCategory(updatedProduct.getCategory());
        existing.setPrice(updatedProduct.getPrice());
        existing.setStock(updatedProduct.getStock());
        return productRepository.save(existing);
    }

    public void deleteProduct(Long id) {
        Product existing = getProductById(id);
        productRepository.delete(existing);
    }

    /**
     * Called by order-service when an order is placed. Kept as a simple
     * synchronous, transactional decrement here since this is a demo app;
     * a production system would likely use an event/saga-based approach
     * to keep services decoupled.
     */
    @Transactional
    public Product reduceStock(Long id, int quantity) {
        Product product = getProductById(id);
        if (product.getStock() < quantity) {
            throw new InsufficientStockException(
                    "Insufficient stock for product '" + product.getName() + "'. Available: "
                            + product.getStock() + ", Requested: " + quantity);
        }
        product.setStock(product.getStock() - quantity);
        return productRepository.save(product);
    }
}
